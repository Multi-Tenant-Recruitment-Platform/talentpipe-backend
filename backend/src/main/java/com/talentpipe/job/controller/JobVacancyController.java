package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.groups.Default;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * (DECISIONS.md). {@code @PreAuthorize} on this class rejects the wrong ROLE
 * with 403 before any work happens: company admins and HR managers manage
 * vacancies; interviewers and candidates do not. {@link JobVacancyService}
 * then independently scopes every lookup to the caller's TENANT, so a vacancy
 * in another company answers 404, never 403.</p>
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
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
public class JobVacancyController {

    private final JobVacancyService vacancyService;

    public JobVacancyController(JobVacancyService vacancyService) {
        this.vacancyService = vacancyService;
    }

    // ------------------------------------------------------------------ reads

    /**
     * The workspace's vacancies, newest first.
     *
     * @param status optional filter: one of DRAFT, PUBLISHED, CLOSED, ARCHIVED,
     *               or ACTIVE for everything not archived. Omitted, every
     *               vacancy is returned, archived included.
     * @param size   page size, capped at 100
     */
    @GetMapping
    public PageResponse<JobVacancyResponse> list(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(vacancyService.list(principal.tenantId(), status, page, size));
    }

    /**
     * Vacancy totals per status, e.g. {@code {"DRAFT":3,"PUBLISHED":12,"CLOSED":3,"ARCHIVED":4}}.
     * Archived vacancies are included — this is where reporting sees them.
     */
    @GetMapping("/counts")
    public Map<VacancyStatus, Long> counts(@AuthenticationPrincipal UserPrincipal principal) {
        return vacancyService.countByStatus(principal.tenantId());
    }

    /** One vacancy. 404 when it does not exist or belongs to another workspace. */
    @GetMapping("/{id}")
    public JobVacancyResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                  @PathVariable UUID id) {
        return vacancyService.get(principal.tenantId(), id);
    }

    // ----------------------------------------------------------------- writes

    /** Files a new vacancy, as a DRAFT or straight to PUBLISHED. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobVacancyResponse create(
            @AuthenticationPrincipal UserPrincipal principal,
            @Validated({Default.class, JobVacancyRequest.OnCreate.class})
            @RequestBody JobVacancyRequest request) {
        return vacancyService.create(principal.tenantId(), principal.id(), request);
    }

    /**
     * Replaces a vacancy's content; status is untouched. 409 when the
     * {@code version} sent is stale, 422 when the vacancy is closed or archived.
     */
    @PutMapping("/{id}")
    public JobVacancyResponse update(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Validated({Default.class, JobVacancyRequest.OnUpdate.class})
            @RequestBody JobVacancyRequest request) {
        return vacancyService.update(principal.tenantId(), principal.id(), id, request);
    }

    // -------------------------------------------------------------- lifecycle

    /** DRAFT → PUBLISHED. 422 if the vacancy is incomplete or is not a draft. */
    @PostMapping("/{id}/publish")
    public JobVacancyResponse publish(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id) {
        return vacancyService.publish(principal.tenantId(), principal.id(), id);
    }

    /** PUBLISHED → CLOSED. New applications stop; received ones are kept. */
    @PostMapping("/{id}/close")
    public JobVacancyResponse close(@AuthenticationPrincipal UserPrincipal principal,
                                    @PathVariable UUID id) {
        return vacancyService.close(principal.tenantId(), principal.id(), id);
    }

    /** CLOSED → ARCHIVED. Leaves the active lists; kept for reporting. */
    @PostMapping("/{id}/archive")
    public JobVacancyResponse archive(@AuthenticationPrincipal UserPrincipal principal,
                                      @PathVariable UUID id) {
        return vacancyService.archive(principal.tenantId(), principal.id(), id);
    }

    /**
     * Creates a new DRAFT pre-filled from this vacancy and returns it — 201,
     * because a new vacancy now exists. The source is not changed.
     */
    @PostMapping("/{id}/duplicate")
    @ResponseStatus(HttpStatus.CREATED)
    public JobVacancyResponse duplicate(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID id) {
        return vacancyService.duplicate(principal.tenantId(), principal.id(), id);
    }
}
