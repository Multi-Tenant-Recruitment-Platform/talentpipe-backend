package com.talentpipe.application.controller;

import com.talentpipe.application.dto.ApplicantPage;
import com.talentpipe.application.dto.ApplicantSummary;
import com.talentpipe.application.service.ApplicantQueryService;
import com.talentpipe.security.RequireCompanyMember;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A vacancy's applicants, for the recruiter's dashboard.
 *
 * <p>Open to any company member; who sees which vacancy's applicants is the
 * service's rule — everyone with {@code applications.view.all}, otherwise
 * only the people who work on the vacancy. Anything else answers 404. The
 * tenant always comes from the access token.</p>
 */
@RestController
@RequestMapping("/api/v1/jobs/{jobId}/applicants")
public class ApplicantController {

    private final ApplicantQueryService applicants;

    public ApplicantController(ApplicantQueryService applicants) {
        this.applicants = applicants;
    }

    /**
     * One page of the vacancy's applicants, ranked, with the counts per group.
     *
     * @param band      all (default), auto, manual, pending, rejected or withdrawn
     * @param q         part of a name or email, ignoring case; ignored in blind mode
     * @param sort      default (by group, then score), score, appliedAt or name
     * @param direction asc or desc; ignored by the default order
     * @param size      capped at 100
     */
    @RequireCompanyMember
    @GetMapping
    @SuppressWarnings("java:S107") // one parameter per query-string option
    public ApplicantPage list(Authentication authentication,
                              @PathVariable UUID jobId,
                              @RequestParam(required = false) String band,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) String sort,
                              @RequestParam(required = false) String direction,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return applicants.list(ApplicantViewers.of(authentication), jobId, band, q, sort, direction, page, size);
    }

    /** The counts and when anything last changed — cheap, for polling. */
    @RequireCompanyMember
    @GetMapping("/summary")
    public ApplicantSummary summary(Authentication authentication, @PathVariable UUID jobId) {
        return applicants.summary(ApplicantViewers.of(authentication), jobId);
    }
}
