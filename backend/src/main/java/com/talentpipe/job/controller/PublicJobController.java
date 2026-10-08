package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobFilterOptionsResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.service.PublicJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The public job board (PB-017): browse, search and filter published
 * vacancies, and read one advert. No authentication — these endpoints sit
 * under {@code /api/v1/public/**}, which the security configuration opens to
 * everyone.
 *
 * <p>Only PUBLISHED vacancies are ever served. A draft, closed or archived
 * vacancy answers 404 here, the same as an id that was never issued, so
 * nothing about a company's unpublished work can be probed from outside.</p>
 *
 * <p>Responses are never cached, so what a recruiter publishes, edits or
 * closes is what the next request sees.</p>
 */
@RestController
@RequestMapping("/api/v1/public/jobs")
public class PublicJobController {

    private final PublicJobService publicJobService;

    public PublicJobController(PublicJobService publicJobService) {
        this.publicJobService = publicJobService;
    }

    /**
     * Published vacancies across all companies, a page at a time.
     *
     * <p>All three filters are optional and combine with AND. With none, this
     * is the plain board, newest first. Re-request with new values whenever a
     * filter changes — each combination is served from an index.</p>
     *
     * @param q        keywords, matched against title, skills, summary and
     *                 description. Results are ranked by relevance. Supports
     *                 {@code "quoted phrases"}, {@code OR} and {@code -exclusion}.
     * @param category a department, matched whole and case-insensitively; use
     *                 a value from {@link #filters()}
     * @param location matched anywhere in the vacancy's location,
     *                 case-insensitively — {@code colombo} finds "Colombo, Sri Lanka"
     * @param size     page size, capped at 50
     */
    @GetMapping
    public PageResponse<JobSummaryResponse> listPublishedJobs(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String location,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(publicJobService.search(q, category, location, page, size));
    }

    /**
     * The categories and locations that currently have published vacancies,
     * each with its count — what the board's filter controls are filled from.
     */
    @GetMapping("/filters")
    public JobFilterOptionsResponse filters() {
        return publicJobService.filterOptions();
    }

    /**
     * One published advert in full.
     *
     * @param slugOrId the {@code slug} from a board card, or the vacancy's id
     */
    @GetMapping("/{slugOrId}")
    public JobDetailResponse getPublishedJob(@PathVariable String slugOrId) {
        return publicJobService.getPublished(slugOrId);
    }
}
