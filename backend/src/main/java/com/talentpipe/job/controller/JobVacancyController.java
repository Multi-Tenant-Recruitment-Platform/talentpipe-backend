package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.HiringTeamRequest;
import com.talentpipe.job.dto.HiringTeamResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.job.service.VacancyHiringTeamService;
import com.talentpipe.job.service.VacancyListFilter;
import com.talentpipe.job.service.VacancyViewer;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import com.talentpipe.security.RequireCompanyMember;
import com.talentpipe.security.RequirePermission;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Vacancy management for the recruiter dashboard (PB-011, PB-018 → PB-022).
 * The frontend's half of this contract is {@code src/api/jobs.ts}.
 *
 * <p><strong>Authorization is enforced twice</strong>, per platform convention
 * (DECISIONS.md). The annotation on each endpoint rejects a caller who lacks
 * the permission with 403 before any work happens: any company member may
 * read, {@code jobs.manage} creates, edits and staffs, {@code jobs.publish}
 * publishes, closes and archives. {@link JobVacancyService} then scopes every
 * lookup to what the caller may see: their company's vacancies with
 * {@code jobs.view.all}, otherwise only the ones they work on. Anything
 * outside that answers 404, never 403.</p>
 *
 * <p><strong>The tenant always comes from the access token</strong> — never
 * from the path, a query parameter or the body.</p>
 *
 * <p><strong>Lifecycle moves are verbs on the resource</strong>
 * ({@code /publish}, {@code /close}, {@code /archive}) rather than a status
 * field on PUT. Each move has its own rule — publishing re-validates the whole
 * advert, closing stops applications — and a dedicated endpoint gives that
 * rule one unambiguous place to live. PUT therefore never changes status.</p>
 */
@RestController
@RequestMapping("/api/v1/jobs")
public class JobVacancyController {

    private final JobVacancyService vacancyService;
    private final VacancyHiringTeamService hiringTeamService;

    public JobVacancyController(JobVacancyService vacancyService, VacancyHiringTeamService hiringTeamService) {
        this.vacancyService = vacancyService;
        this.hiringTeamService = hiringTeamService;
    }

    // ------------------------------------------------------------------ reads

    /**
     * The vacancies the caller may see, newest first: the whole company's with
     * {@code jobs.view.all}, otherwise the ones they work on.
     *
     * @param status     one of DRAFT, PUBLISHED, CLOSED, ARCHIVED, or ACTIVE
     *                   for everything not archived. Omitted, every vacancy is
     *                   returned, archived included.
     * @param assignedTo {@code me}, or a member's id: only vacancies that
     *                   person recruits for, manages or is on the hiring team of
     * @param department only this department, matched whole, ignoring case
     * @param size       page size, capped at 100
     */
    @RequireCompanyMember
    @GetMapping
    public PageResponse<JobVacancyResponse> list(
            Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(vacancyService.list(viewer(authentication),
                new VacancyListFilter(status, assignedTo, department), page, size));
    }

    /**
     * Totals per status of the vacancies the caller may see, e.g.
     * {@code {"DRAFT":3,"PUBLISHED":12,"CLOSED":3,"ARCHIVED":4}}. Archived
     * vacancies are included — this is where reporting sees them.
     */
    @RequireCompanyMember
    @GetMapping("/counts")
    public Map<VacancyStatus, Long> counts(Authentication authentication) {
        return vacancyService.countByStatus(viewer(authentication));
    }

    /** One vacancy. 404 when it does not exist, belongs to another company, or is not the caller's to see. */
    @RequireCompanyMember
    @GetMapping("/{id}")
    public JobVacancyResponse get(Authentication authentication, @PathVariable UUID id) {
        return vacancyService.get(viewer(authentication), id);
    }

    /** Who works on the vacancy: its recruiter, hiring manager and hiring team. */
    @RequireCompanyMember
    @GetMapping("/{id}/hiring-team")
    public HiringTeamResponse hiringTeam(Authentication authentication, @PathVariable UUID id) {
        return hiringTeamService.get(viewer(authentication), id);
    }

    // ----------------------------------------------------------------- writes

    /** Files a new vacancy, as a DRAFT or straight to PUBLISHED. The caller is recorded as its creator. */
    @RequirePermission(Permission.Keys.JOBS_MANAGE)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobVacancyResponse create(
            Authentication authentication,
            @Validated({Default.class, JobVacancyRequest.OnCreate.class})
            @RequestBody JobVacancyRequest request) {
        return vacancyService.create(viewer(authentication), request);
    }

    /**
     * Replaces a vacancy's content; status is untouched. 409 when the
     * {@code version} sent is stale, 422 when the vacancy is closed or archived.
     */
    @RequirePermission(Permission.Keys.JOBS_MANAGE)
    @PutMapping("/{id}")
    public JobVacancyResponse update(
            Authentication authentication,
            @PathVariable UUID id,
            @Validated({Default.class, JobVacancyRequest.OnUpdate.class})
            @RequestBody JobVacancyRequest request) {
        return vacancyService.update(viewer(authentication), id, request);
    }

    /**
     * Replaces the hiring team. 422 when the vacancy is archived or someone
     * being added is not an active member of the workspace.
     */
    @RequirePermission(Permission.Keys.JOBS_MANAGE)
    @PutMapping("/{id}/hiring-team")
    public HiringTeamResponse replaceHiringTeam(Authentication authentication,
                                                @PathVariable UUID id,
                                                @Valid @RequestBody HiringTeamRequest request) {
        return hiringTeamService.replace(viewer(authentication), id, request);
    }

    // -------------------------------------------------------------- lifecycle

    /** DRAFT → PUBLISHED. 422 if the vacancy is incomplete or is not a draft. */
    @RequirePermission(Permission.Keys.JOBS_PUBLISH)
    @PostMapping("/{id}/publish")
    public JobVacancyResponse publish(Authentication authentication, @PathVariable UUID id) {
        return vacancyService.publish(viewer(authentication), id);
    }

    /** PUBLISHED → CLOSED. New applications stop; received ones are kept. */
    @RequirePermission(Permission.Keys.JOBS_PUBLISH)
    @PostMapping("/{id}/close")
    public JobVacancyResponse close(Authentication authentication, @PathVariable UUID id) {
        return vacancyService.close(viewer(authentication), id);
    }

    /** CLOSED → ARCHIVED. Leaves the active lists; kept for reporting. */
    @RequirePermission(Permission.Keys.JOBS_PUBLISH)
    @PostMapping("/{id}/archive")
    public JobVacancyResponse archive(Authentication authentication, @PathVariable UUID id) {
        return vacancyService.archive(viewer(authentication), id);
    }

    /**
     * Creates a new DRAFT pre-filled from this vacancy and returns it — 201,
     * because a new vacancy now exists. The source is not changed.
     */
    @RequirePermission(Permission.Keys.JOBS_MANAGE)
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public JobVacancyResponse duplicate(Authentication authentication, @PathVariable UUID id) {
        return vacancyService.duplicate(viewer(authentication), id);
    }

    // ---------------------------------------------------------------- private

    /** The caller, and whether their role lets them see every vacancy in the company. */
    private static VacancyViewer viewer(Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return new VacancyViewer(principal.tenantId(), principal.id(),
                Authorities.has(authentication, Permission.JOBS_VIEW_ALL));
    }
}
