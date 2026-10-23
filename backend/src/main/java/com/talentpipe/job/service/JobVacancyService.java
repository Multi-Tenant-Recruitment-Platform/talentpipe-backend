package com.talentpipe.job.service;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.VacancyFeaturesRequest;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.VacancySpecifications;
import com.talentpipe.job.repository.VacancyStatusCount;
import com.talentpipe.job.validator.JobVacancyMemberValidator;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import com.talentpipe.job.validator.JobVacancyShapeValidator;
import com.talentpipe.tenant.dto.TenantSettingsResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recruiter-side use cases for vacancies: create, read, edit, and the
 * lifecycle moves — publish, close, archive, duplicate (PB-011, PB-018 →
 * PB-022).
 *
 * <p><strong>Scope.</strong> Every method takes a {@link VacancyViewer} the
 * controller built from the access token, and every load goes through
 * {@link VacancyAccess}, which looks the vacancy up by id, company <em>and</em>
 * what the viewer may see. A vacancy in another company, or one a member
 * without {@code jobs.view.all} does not work on, therefore answers 404,
 * exactly like one that does not exist. This is the service-layer half of the
 * platform's double check (DECISIONS.md); the permission half is
 * {@code @RequirePermission} on the controller.</p>
 *
 * <p><strong>Where the rules live.</strong> Request shapes, completeness and
 * membership rules are guarded by dedicated SRP validators:
 * {@link JobVacancyShapeValidator}, {@link JobVacancyPublishValidator}, and
 * {@link JobVacancyMemberValidator}.</p>
 *
 * <p><strong>Every write returns the vacancy as now stored.</strong> Writes
 * use {@code saveAndFlush} so the response carries the bumped {@code version}
 * and fresh {@code updatedAt} that Hibernate only assigns at flush.</p>
 */
@Service
public class JobVacancyService {

    private static final Logger log = LoggerFactory.getLogger(JobVacancyService.class);

    /** The largest page of vacancies the dashboard may ask for. */
    static final int MAX_PAGE_SIZE = 100;

    /** List filter value meaning "every state except ARCHIVED" — the active lists. */
    static final String ACTIVE_FILTER = "ACTIVE";

    /** {@code assignedTo} value meaning the caller. */
    static final String ME = "me";

    /** Newest first; id breaks ties so two vacancies created in the same instant page stably. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobVacancyRepository vacancyRepository;
    private final VacancyAccess access;
    private final VacancyHiringTeamService hiringTeam;
    private final VacancyIndustryPolicy industryPolicy;
    private final VacancyContentNormalizer normalizer;
    private final JobVacancyShapeValidator shapeValidator;
    private final JobVacancyPublishValidator publishValidator;
    private final JobVacancyMemberValidator memberValidator;
    private final VacancyCalendar calendar;
    private final UserDirectoryService userDirectory;
    private final TenantService tenantService;
    private final JobVacancyMapper mapper;

    public JobVacancyService(JobVacancyRepository vacancyRepository,
                             VacancyAccess access,
                             VacancyHiringTeamService hiringTeam,
                             VacancyIndustryPolicy industryPolicy,
                             VacancyContentNormalizer normalizer,
                             JobVacancyShapeValidator shapeValidator,
                             JobVacancyPublishValidator publishValidator,
                             JobVacancyMemberValidator memberValidator,
                             VacancyCalendar calendar,
                             UserDirectoryService userDirectory,
                             TenantService tenantService,
                             JobVacancyMapper mapper) {
        this.vacancyRepository = vacancyRepository;
        this.access = access;
        this.hiringTeam = hiringTeam;
        this.industryPolicy = industryPolicy;
        this.normalizer = normalizer;
        this.shapeValidator = shapeValidator;
        this.publishValidator = publishValidator;
        this.memberValidator = memberValidator;
        this.calendar = calendar;
        this.userDirectory = userDirectory;
        this.tenantService = tenantService;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ reads

    /**
     * A page of the vacancies the viewer may see, newest first: the whole
     * company's with {@code jobs.view.all}, otherwise the ones they work on.
     *
     * <p>With no filter this returns every such vacancy, <strong>archived
     * included</strong>: the dashboard loads the whole set and sorts it into
     * its own tabs. Archiving hides a vacancy from the <em>active</em> lists,
     * not from the data — pass {@code ACTIVE} to get exactly those lists
     * server-side, or a single status to get one tab.</p>
     *
     * @param filter which vacancies to list; every part is optional
     * @throws InvalidRequestException (400) if the status or assignee filter
     *         is not one of the accepted values
     */
    @Transactional(readOnly = true)
    public Page<JobVacancyResponse> list(VacancyViewer viewer, VacancyListFilter filter, int page, int size) {
        Pageable pageable = Paging.of(page, size, MAX_PAGE_SIZE, NEWEST_FIRST);

        Specification<JobVacancy> query = VacancySpecifications.inTenant(viewer.tenantId());
        if (!viewer.seesAllVacancies()) {
            query = query.and(VacancySpecifications.worksOn(viewer.userId()));
        }
        Optional<Set<VacancyStatus>> statuses = statusesFor(filter.status());
        if (statuses.isPresent()) {
            query = query.and(VacancySpecifications.statusIn(statuses.get()));
        }
        Optional<UUID> assignee = assigneeFor(filter.assignedTo(), viewer);
        if (assignee.isPresent()) {
            query = query.and(VacancySpecifications.assignedTo(assignee.get()));
        }
        if (filter.department() != null && !filter.department().isBlank()) {
            query = query.and(VacancySpecifications.inDepartment(filter.department()));
        }
        return vacancyRepository.findAll(query, pageable).map(mapper::toResponse);
    }

    /**
     * How many vacancies the viewer can see in each state — the retained
     * totals that reporting reads (PB-022). Archived vacancies are counted
     * like any other; a state with none is reported as zero rather than left
     * out, so the response always has the same four keys.
     */
    @Transactional(readOnly = true)
    public Map<VacancyStatus, Long> countByStatus(VacancyViewer viewer) {
        Map<VacancyStatus, Long> counts = new EnumMap<>(VacancyStatus.class);
        for (VacancyStatus status : VacancyStatus.values()) {
            counts.put(status, 0L);
        }
        List<VacancyStatusCount> rows = viewer.seesAllVacancies()
                ? vacancyRepository.countByStatus(viewer.tenantId())
                : vacancyRepository.countWorkedOnByStatus(viewer.tenantId(), viewer.userId());
        rows.forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    /** @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see */
    @Transactional(readOnly = true)
    public JobVacancyResponse get(VacancyViewer viewer, UUID vacancyId) {
        return mapper.toResponse(access.require(viewer, vacancyId));
    }

    // ----------------------------------------------------------------- writes

    /**
     * Files a new vacancy, as a DRAFT or straight to PUBLISHED.
     *
     * <p>"Straight to published" is not a second way in: the vacancy is built
     * as a draft and published in the same transaction, so it passes exactly
     * the checks a later publish would and gets its {@code publishedAt} in
     * the same request.</p>
     *
     * @throws InvalidRequestException (400) if the requested status is
     *         anything but DRAFT or PUBLISHED
     * @throws BusinessRuleException   (422) if a content rule fails, or the
     *         vacancy is to be published and is not complete
     */
    @Transactional
    public JobVacancyResponse create(VacancyViewer viewer, JobVacancyRequest request) {
        UUID tenantId = viewer.tenantId();
        shapeValidator.validateCreationShape(request);
        memberValidator.validateMembers(tenantId, request.assignedRecruiterId(), request.hiringManagerId());

        LocalDate today = calendar.today(tenantId);
        if (request.status() == VacancyStatus.PUBLISHED) {
            publishValidator.validateForPublish(request, today);
        }

        VacancyContent content = normalizer.normalize(request);
        JobVacancy vacancy = new JobVacancy(tenantId, viewer.userId(), content);
        vacancy.fileUnder(industryPolicy.resolve(tenantId, request.industry(), null));
        startFeatures(vacancy, tenantId, request.aiScreeningEnabled(), request.candidateMatchingEnabled());
        if (request.status() == VacancyStatus.PUBLISHED) {
            vacancy.publish(calendar.now(), today);
        }
        return stored(vacancy, viewer, "created as " + request.status());
    }

    /**
     * Replaces a vacancy's content (PB-019). Never changes its status.
     *
     * <p>The change is visible everywhere at once: this response, the
     * dashboard list and — for a live vacancy — the public board and its
     * keyword search all read the same row, and the search document is a
     * column PostgreSQL regenerates in this same transaction. There is no
     * cache or secondary index to fall behind.</p>
     *
     * <p>Refusals are ordered from most to least fundamental, so the caller
     * hears the reason that matters: not found, then not editable, then
     * stale, then invalid content.</p>
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     * @throws BusinessRuleException     (422) closed or archived; a content
     *         rule fails; or the vacancy is live and would become incomplete
     * @throws ConcurrentUpdateException (409) {@code request.version()} is not
     *         the stored version — someone else saved first
     */
    @Transactional
    public JobVacancyResponse update(VacancyViewer viewer, UUID vacancyId, JobVacancyRequest request) {
        UUID tenantId = viewer.tenantId();
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.requireEditable();
        if (!Objects.equals(request.version(), vacancy.getVersion())) {
            throw new ConcurrentUpdateException(
                    "Someone else saved changes to this vacancy after you opened it. "
                            + "Reload it to see their version, then make your changes again.");
        }

        shapeValidator.validateUpdateShape(request);
        memberValidator.validateMembersForUpdate(
                tenantId,
                request.assignedRecruiterId(), vacancy.content().assignedRecruiterId(),
                request.hiringManagerId(), vacancy.content().hiringManagerId()
        );

        LocalDate today = calendar.today(tenantId);
        if (vacancy.getStatus() == VacancyStatus.PUBLISHED) {
            publishValidator.validateForStayComplete(request, today);
        }

        String industry = industryPolicy.resolve(tenantId, request.industry(), vacancy.getIndustry());
        VacancyContent content = normalizer.normalize(request);
        vacancy.applyContent(content, today);
        vacancy.fileUnder(industry);
        vacancy.switchFeatures(request.aiScreeningEnabled(), request.candidateMatchingEnabled());
        return stored(vacancy, viewer, "edited");
    }

    /**
     * DRAFT → PUBLISHED (PB-018). From the moment this commits the vacancy is
     * on the candidate portal: the public board reads this table directly, so
     * there is nothing further to synchronize.
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     * @throws BusinessRuleException     (422) not a draft, or not complete
     */
    @Transactional
    public JobVacancyResponse publish(VacancyViewer viewer, UUID vacancyId) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        LocalDate today = calendar.today(viewer.tenantId());
        publishValidator.validateForPublish(vacancy, today);
        vacancy.publish(calendar.now(), today);
        return stored(vacancy, viewer, "published");
    }

    /**
     * PUBLISHED → CLOSED (PB-020). The vacancy drops off the candidate portal
     * and {@link VacancyApplicationGate} refuses new applications from here
     * on; applications already received are not touched.
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     * @throws BusinessRuleException     (422) not published
     */
    @Transactional
    public JobVacancyResponse close(VacancyViewer viewer, UUID vacancyId) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.close(calendar.now());
        return stored(vacancy, viewer, "closed");
    }

    /**
     * CLOSED → ARCHIVED (PB-022). The vacancy leaves the active lists
     * ({@code status=ACTIVE}) and stays in the data: it is still returned by
     * id, by the unfiltered list, by {@code status=ARCHIVED} and in
     * {@link #countByStatus}. Nothing in this module ever deletes a vacancy.
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     * @throws BusinessRuleException     (422) not closed
     */
    @Transactional
    public JobVacancyResponse archive(VacancyViewer viewer, UUID vacancyId) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.archive(calendar.now());
        return stored(vacancy, viewer, "archived");
    }

    /**
     * Creates a new DRAFT pre-filled from an existing vacancy (PB-021).
     *
     * <p>The source is only read, never changed, so it may be in any state —
     * an archived vacancy is a perfectly good template. The copy starts a life
     * of its own: new id, DRAFT, version 0, no lifecycle timestamps. Being a
     * draft, it is fully editable before anyone publishes it. What is and is
     * not carried over is decided by {@link VacancyContent#asDuplicate}; the
     * hiring team comes along too, minus anyone no longer active. The person
     * duplicating it is recorded as the copy's creator.</p>
     *
     * @throws ResourceNotFoundException (404) source missing, another company's, or not the viewer's to see
     */
    @Transactional
    public JobVacancyResponse duplicate(VacancyViewer viewer, UUID vacancyId) {
        UUID tenantId = viewer.tenantId();
        JobVacancy source = access.require(viewer, vacancyId);
        VacancyContent template = source.content()
                .asDuplicate(userId -> userDirectory.isActiveMember(tenantId, userId));
        JobVacancy draft = new JobVacancy(tenantId, viewer.userId(), template);
        draft.fileUnder(industryPolicy.forDuplicate(tenantId, source.getIndustry()));
        draft.switchFeatures(source.isAiScreeningEnabled(), source.isCandidateMatchingEnabled());
        JobVacancy copy = save(draft, viewer, "duplicated from " + vacancyId);
        hiringTeam.copyTeam(tenantId, source.getId(), copy.getId(), viewer.userId());
        return mapper.toResponse(copy);
    }

    /**
     * Turns the vacancy's features on or off (PB-015); a switch left out keeps
     * its value. No version is needed: each switch is a single value, so two
     * people flipping different ones cannot lose each other's change.
     *
     * @throws ResourceNotFoundException (404) missing, another company's, or not the viewer's to see
     * @throws BusinessRuleException     (422) the vacancy is closed or archived
     */
    @Transactional
    public JobVacancyResponse switchFeatures(VacancyViewer viewer, UUID vacancyId, VacancyFeaturesRequest request) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        vacancy.switchFeatures(request.aiScreeningEnabled(), request.candidateMatchingEnabled());
        return stored(vacancy, viewer, "features switched");
    }

    // ---------------------------------------------------------------- private

    /** A new vacancy's switches: as requested, or the company's default for any left out. */
    private void startFeatures(JobVacancy vacancy, UUID tenantId, Boolean aiScreening, Boolean candidateMatching) {
        if (aiScreening != null && candidateMatching != null) {
            vacancy.switchFeatures(aiScreening, candidateMatching);
            return;
        }
        TenantSettingsResponse defaults = tenantService.getSettings(tenantId);
        vacancy.switchFeatures(
                aiScreening != null ? aiScreening : defaults.aiScreeningDefault(),
                candidateMatching != null ? candidateMatching : defaults.candidateMatchingDefault());
    }

    /** {@link #save}, answered as the stored vacancy. */
    private JobVacancyResponse stored(JobVacancy vacancy, VacancyViewer viewer, String action) {
        return mapper.toResponse(save(vacancy, viewer, action));
    }

    /**
     * Writes the vacancy through to the database and records who did what.
     * The audit line carries ids and the action only — never advert text.
     */
    private JobVacancy save(JobVacancy vacancy, VacancyViewer viewer, String action) {
        JobVacancy saved = vacancyRepository.saveAndFlush(vacancy);
        log.info("Vacancy {} {} [tenant={}, user={}, status={}]",
                saved.getId(), action, viewer.tenantId(), viewer.userId(), saved.getStatus());
        return saved;
    }

    /**
     * Resolves the list's {@code status} filter to the states it selects.
     *
     * @return empty when no filter was given (every state)
     */
    static Optional<Set<VacancyStatus>> statusesFor(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return Optional.empty();
        }
        String wanted = statusFilter.trim().toUpperCase(Locale.ROOT);
        if (ACTIVE_FILTER.equals(wanted)) {
            return Optional.of(EnumSet.complementOf(EnumSet.of(VacancyStatus.ARCHIVED)));
        }
        try {
            return Optional.of(EnumSet.of(VacancyStatus.valueOf(wanted)));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                    "status must be one of DRAFT, PUBLISHED, CLOSED, ARCHIVED or ACTIVE.");
        }
    }

    /**
     * Resolves the list's {@code assignedTo} filter: {@code me} for the
     * viewer, or a member's id.
     *
     * @return empty when no filter was given
     */
    private static Optional<UUID> assigneeFor(String assignedTo, VacancyViewer viewer) {
        if (assignedTo == null || assignedTo.isBlank()) {
            return Optional.empty();
        }
        String wanted = assignedTo.trim();
        if (ME.equalsIgnoreCase(wanted)) {
            return Optional.of(viewer.userId());
        }
        try {
            return Optional.of(UUID.fromString(wanted));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("assignedTo must be \"me\" or the id of a member of your workspace.");
        }
    }
}
