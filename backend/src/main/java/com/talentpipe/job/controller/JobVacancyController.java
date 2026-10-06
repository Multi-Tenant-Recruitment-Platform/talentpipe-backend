package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.service.JobVacancyService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for job vacancy management.
 */
@RestController
@RequestMapping("/api/v1/jobs")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
public class JobVacancyController {

    private final JobVacancyService service;

    public JobVacancyController(JobVacancyService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<JobVacancyResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(PageResponse.from(service.list(principal.tenantId(), page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobVacancyResponse> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.get(id, principal.tenantId()));
    }

    @PostMapping
    public ResponseEntity<JobVacancyResponse> create(
            @RequestBody @Valid JobVacancyRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JobVacancyResponse created = service.create(request, principal.tenantId());
        URI location = URI.create("/api/v1/jobs/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<JobVacancyResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid JobVacancyUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.update(id, request, principal.tenantId()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<JobVacancyResponse> publish(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.publish(id, principal.tenantId()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<JobVacancyResponse> close(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.close(id, principal.tenantId()));
    }

    @PostMapping("/{id}/archive")
    public ResponseEntity<JobVacancyResponse> archive(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return ResponseEntity.ok(service.archive(id, principal.tenantId()));
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<JobVacancyResponse> duplicate(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        JobVacancyResponse duplicated = service.duplicate(id, principal.tenantId());
        URI location = URI.create("/api/v1/jobs/" + duplicated.id());
        return ResponseEntity.created(location).body(duplicated);
    }
}
