package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) job board API (PB-005, partial). The contract
 * exists now so the public site can render its "browse jobs" page from day
 * one.
 */
@RestController
@RequestMapping("/api/v1/public/jobs")
public class PublicJobController {

    /**
     * Lists published jobs across all tenants.
     *
     * <p>TODO(sprint2): back this with real published jobs once the Job
     * module lands in a later sprint. Until then it returns a well-formed
     * empty page — deliberately NOT an error, so clients can already build
     * against the final contract.</p>
     */
    @GetMapping
    public PageResponse<JobSummaryResponse> listPublishedJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.empty(page, size);
    }
}
