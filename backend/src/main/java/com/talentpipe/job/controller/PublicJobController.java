package com.talentpipe.job.controller;

import com.talentpipe.common.dto.PageResponse;
import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobFilterOptionsResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.dto.PublicJobSearchParams;
import com.talentpipe.job.service.PublicJobService;
import jakarta.validation.Valid;
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
     * <p>Every filter is optional and they combine with AND. With none, this
     * is the plain board, newest first. Re-request with new values whenever a
     * filter changes: the board filters on the server, over every published
     * vacancy, not just the page already loaded.</p>
     *
     * <ul>
     *   <li>{@code q} — keywords, matched against title, skills, summary and
     *       description. Supports {@code "quoted phrases"}, {@code OR} and
     *       {@code -exclusion}.</li>
     *   <li>{@code category} — a department, matched whole, ignoring case;
     *       use a value from {@link #filters()}.</li>
     *   <li>{@code location} — matched anywhere in the vacancy's location,
     *       ignoring case: {@code colombo} finds "Colombo, Sri Lanka".</li>
     *   <li>{@code employmentType}, {@code workplaceType} — any of the given
     *       values; repeat the parameter or separate values with commas.</li>
     *   <li>{@code minExperience}, {@code maxExperience} — years the vacancy
     *       asks for; one that names none asks for none.</li>
     *   <li>{@code salaryOnly} — only vacancies that state their pay.</li>
     *   <li>{@code payPeriod}, {@code currency} (e.g. {@code LKR}),
     *       {@code salaryMin}, {@code salaryMax} — stated pay that overlaps
     *       the range. A range needs a pay period.</li>
     *   <li>{@code sort} — {@code relevant} (default: best keyword match
     *       first) or {@code newest}.</li>
     *   <li>{@code size} — page size, capped at 50.</li>
     * </ul>
     */
    @GetMapping
    public PageResponse<JobSummaryResponse> listPublishedJobs(
            @Valid PublicJobSearchParams search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(publicJobService.search(search.toCriteria(), page, size));
    }

    /**
     * The categories, locations, employment and workplace types that
     * currently have published vacancies, each with its count, and the span
     * of pay they state: what the board's filter controls are filled from.
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
