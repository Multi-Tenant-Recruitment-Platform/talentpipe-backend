package com.talentpipe.application.controller;

import com.talentpipe.application.dto.ApplicationResponse;
import com.talentpipe.application.dto.UpdateApplicationStatusRequest;
import com.talentpipe.application.service.ApplicationService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/jobs/{jobId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse submit(@AuthenticationPrincipal UserPrincipal principal,
                                       @PathVariable UUID jobId) {
        return applicationService.submit(principal.id(), jobId);
    }

    @GetMapping
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<ApplicationResponse> mine(@AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.forCandidate(principal.id());
    }

    @GetMapping("/{applicationId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse get(@AuthenticationPrincipal UserPrincipal principal,
                                   @PathVariable UUID applicationId) {
        return applicationService.getForCandidate(principal.id(), applicationId);
    }

    @DeleteMapping("/{applicationId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public ApplicationResponse withdraw(@AuthenticationPrincipal UserPrincipal principal,
                                        @PathVariable UUID applicationId) {
        return applicationService.withdraw(principal.id(), applicationId);
    }

    @GetMapping("/tenant")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'INTERVIEWER')")
    public List<ApplicationResponse> tenantApplications(@AuthenticationPrincipal UserPrincipal principal) {
        return applicationService.forTenant(principal.tenantId());
    }

    @PatchMapping("/{applicationId}/status")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'INTERVIEWER')")
    public ApplicationResponse updateStatus(@AuthenticationPrincipal UserPrincipal principal,
                                             @PathVariable UUID applicationId,
                                             @Valid @RequestBody UpdateApplicationStatusRequest request) {
        return applicationService.updateStatus(principal.tenantId(), applicationId, request);
    }
}
