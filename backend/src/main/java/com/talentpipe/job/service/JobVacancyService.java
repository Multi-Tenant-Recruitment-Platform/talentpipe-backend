package com.talentpipe.job.service;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ConcurrentUpdateException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.validator.JobVacancyMemberValidator;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import com.talentpipe.job.validator.JobVacancyShapeValidator;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The recruiter-side use cases for vacancies: create, read, edit, and the
 * lifecycle moves — publish, close, archive, duplicate (PB-011, PB-018 →
 * PB-022).
 *
 * <p><strong>Tenant scope.</strong> Every method takes the tenant id that the
 * controller read from the access token, and every load goes through
 * {@link #requireOwned}, which looks the vacancy up by id <em>and</em> tenant.
 * A vacancy belonging to another company therefore answers 404, exactly like
 * one that does not exist — existence never leaks across tenants. This is the
 * service-layer half of the platform's double check (DECISIONS.md); the role
 * half is {@code @PreAuthorize} on the controller.</p>
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

    /** Newest first; id breaks ties so two vacancies created in the same instant page stably. */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final JobVacancyRepository vacancyRepository;
    private final VacancyContentNormalizer normalizer;
    private final JobVacancyShapeValidator shapeValidator;
    private final JobVacancyPublishValidator publishValidator;
    private final JobVacancyMemberValidator memberValidator;
    private final VacancyCalendar calendar;
    private final UserDirectoryService userDirectory;
    private final JobVacancyMapper mapper;

    public JobVacancyService(JobVacancyRepository vacancyRepository,
                             VacancyContentNormalizer normalizer,
                             JobVacancyShapeValidator shapeValidator,
                             JobVacancyPublishValidator publishValidator,
                             JobVacancyMemberValidator memberValidator,
                             VacancyCalendar calendar,
                             UserDirectoryService userDirectory,
                             JobVacancyMapper mapper) {
        this.vacancyRepository = vacancyRepository;
        this.normalizer = normalizer;
        this.shapeValidator = shapeValidator;
        this.publishValidator = publishValidator;
        this.memberValidator = memberValidator;
        this.calendar = calendar;
        this.userDirectory = userDirectory;
        this.mapper = mapper;
    }

    // ------------------------------------------------------------------ reads

    /**
     * A page of the tenant's vacancies, newest first.
     *
     * <p>With no filter this returns every vacancy, <strong>archived
     * included</strong>: the dashboard loads the whole set and sorts it into
     * its own tabs. Archiving hides a vacancy from the <em>active</em> lists,
     * not from the data — pass {@code ACTIVE} to get exactly those lists
     * server-side, or a single status to get one tab.</p>
     *
     * @param statusFilter {@code null}/blank for all, {@code ACTIVE} for
     *                     everything not archived, or one {@link VacancyStatus} name
     * @throws InvalidRequestException (400) if {@code statusFilter} is none of those
     */
    @Transactional(readOnly = true)
    public Page<JobVacancyResponse> list(UUID tenantId, String statusFilter, int page, int size) {
        Pageable pageable = Paging.of(page, size, MAX_PAGE_SIZE, NEWEST_FIRST);
        Page<JobVacancy> vacancies = statusesFor(statusFilter)
                .map(statuses -> vacancyRepository.findByTenantIdAndStatusIn(tenantId, statuses, pageable))
                .orElseGet(() -> vacancyRepository.findByTenantId(tenantId, pageable));
        return vacancies.map(mapper::toResponse);
    }

    /**
     * How many vacancies the tenant has in each state — the retained totals
     * that reporting reads (PB-022). Archived vacancies are counted like any
     * other; a state with none is reported as zero rather than left out, so
     * the response always has the same four keys.
     */
    @Transactional(readOnly = true)
    public Map<VacancyStatus, Long> countByStatus(UUID tenantId) {
        Map<VacancyStatus, Long> counts = new EnumMap<>(VacancyStatus.class);
        for (VacancyStatus status : VacancyStatus.values()) {
            counts.put(status, 0L);
        }
        vacancyRepository.countByStatus(tenantId)
                .forEach(row -> counts.put(row.getStatus(), row.getTotal()));
        return counts;
    }

    /** @throws ResourceNotFoundException (404) if the vacancy is missing or in another tenant */
    @Transactional(readOnly = true)
    public JobVacancyResponse get(UUID tenantId, UUID vacancyId) {
        return mapper.toResponse(requireOwned(tenantId, vacancyId));
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
    public JobVacancyResponse create(UUID tenantId, UUID actorId, JobVacancyRequest request) {
        shapeValidator.validateCreationShape(request);
        memberValidator.validateMembers(tenantId, request.assignedRecruiterId(), request.hiringManagerId());

        LocalDate today = calendar.today(tenantId);
        if (request.status() == VacancyStatus.PUBLISHED) {
            publishValidator.validateForPublish(request, today);
        }

        VacancyContent content = normalizer.normalize(request);
        JobVacancy vacancy = new JobVacancy(tenantId, content);
        if (request.status() == VacancyStatus.PUBLISHED) {
            vacancy.publish(calendar.now(), today);
        }
        return stored(vacancy, tenantId, actorId, "created as " + request.status());
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
     * @throws ResourceNotFoundException (404) missing or another tenant's
     * @throws BusinessRuleException     (422) closed or archived; a content
     *         rule fails; or the vacancy is live and would become incomplete
     * @throws ConcurrentUpdateException (409) {@code request.version()} is not
     *         the stored version — someone else saved first
     */
    @Transactional
    public JobVacancyResponse update(UUID tenantId, UUID actorId, UUID vacancyId, JobVacancyRequest request) {
        JobVacancy vacancy = requireOwned(tenantId, vacancyId);
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

        VacancyContent content = normalizer.normalize(request);
        vacancy.applyContent(content, today);
        return stored(vacancy, tenantId, actorId, "edited");
    }

    /**
     * DRAFT → PUBLISHED (PB-018). From the moment this commits the vacancy is
     * on the candidate portal: the public board reads this table directly, so
     * there is nothing further to synchronize.
     *
     * @throws ResourceNotFoundException (404) missing or another tenant's
     * @throws BusinessRuleException     (422) not a draft, or not complete
     */
    @Transactional
    public JobVacancyResponse publish(UUID tenantId, UUID actorId, UUID vacancyId) {
        JobVacancy vacancy = requireOwned(tenantId, vacancyId);
        LocalDate today = calendar.today(tenantId);
        publishValidator.validateForPublish(vacancy, today);
        vacancy.publish(calendar.now(), today);
        return stored(vacancy, tenantId, actorId, "published");
    }

    /**
     * PUBLISHED → CLOSED (PB-020). The vacancy drops off the candidate portal
     * and {@link VacancyApplicationGate} refuses new applications from here
     * on; applications already received are not touched.
     *
     * @throws ResourceNotFoundException (404) missing or another tenant's
     * @throws BusinessRuleException     (422) not published
     */
    @Transactional
    public JobVacancyResponse close(UUID tenantId, UUID actorId, UUID vacancyId) {
        JobVacancy vacancy = requireOwned(tenantId, vacancyId);
        vacancy.close(calendar.now());
        return stored(vacancy, tenantId, actorId, "closed");
    }

    /**
     * CLOSED → ARCHIVED (PB-022). The vacancy leaves the active lists
     * ({@code status=ACTIVE}) and stays in the data: it is still returned by
     * id, by the unfiltered list, by {@code status=ARCHIVED} and in
     * {@link #countByStatus}. Nothing in this module ever deletes a vacancy.
     *
     * @throws ResourceNotFoundException (404) missing or another tenant's
     * @throws BusinessRuleException     (422) not closed
     */
    @Transactional
    public JobVacancyResponse archive(UUID tenantId, UUID actorId, UUID vacancyId) {
        JobVacancy vacancy = requireOwned(tenantId, vacancyId);
        vacancy.archive(calendar.now());
        return stored(vacancy, tenantId, actorId, "archived");
    }

    /**
     * Creates a new DRAFT pre-filled from an existing vacancy (PB-021).
     *
     * <p>The source is only read, never changed, so it may be in any state —
     * an archived vacancy is a perfectly good template. The copy starts a life
     * of its own: new id, DRAFT, version 0, no lifecycle timestamps. Being a
     * draft, it is fully editable before anyone publishes it. What is and is
     * not carried over is decided by {@link VacancyContent#asDuplicate}.</p>
     *
     * @throws ResourceNotFoundException (404) source missing or another tenant's
     */
    @Transactional
    public JobVacancyResponse duplicate(UUID tenantId, UUID actorId, UUID vacancyId) {
        JobVacancy source = requireOwned(tenantId, vacancyId);
        VacancyContent template = source.content()
                .asDuplicate(userId -> userDirectory.isActiveMember(tenantId, userId));
        return stored(new JobVacancy(tenantId, template), tenantId, actorId, "duplicated from " + vacancyId);
    }

    // ---------------------------------------------------------------- private

    /**
     * Loads a vacancy for the caller's tenant or answers 404. The same
     * exception covers "no such vacancy" and "another company's vacancy", so
     * the two are indistinguishable from outside.
     */
    private JobVacancy requireOwned(UUID tenantId, UUID vacancyId) {
        return vacancyRepository.findByIdAndTenantId(vacancyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Vacancy not found"));
    }

    /**
     * Writes the vacancy through to the database and records who did what.
     * The audit line carries ids and the action only — never advert text.
     */
    private JobVacancyResponse stored(JobVacancy vacancy, UUID tenantId, UUID actorId, String action) {
        JobVacancy saved = vacancyRepository.saveAndFlush(vacancy);
        log.info("Vacancy {} {} [tenant={}, user={}, status={}]",
                saved.getId(), action, tenantId, actorId, saved.getStatus());
        return mapper.toResponse(saved);
    }

    /**
     * Resolves the list's {@code status} filter to the states it selects.
     *
     * @return empty when no filter was given (every state)
     */
    private static Optional<Set<VacancyStatus>> statusesFor(String statusFilter) {
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
}
