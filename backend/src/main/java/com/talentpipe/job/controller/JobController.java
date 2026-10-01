package com.talentpipe.job.controller;

import com.talentpipe.job.dto.CreateJobRequest;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.service.JobService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    public JobSummaryResponse create(@AuthenticationPrincipal UserPrincipal principal,
                                     @Valid @RequestBody CreateJobRequest request) {
        return jobService.create(principal.tenantId(), request);
    }
}
