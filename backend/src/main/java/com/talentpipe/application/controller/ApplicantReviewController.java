package com.talentpipe.application.controller;

import com.talentpipe.application.dto.AdvanceToInterviewRequest;
import com.talentpipe.application.dto.ApplicantReview;
import com.talentpipe.application.dto.ApplicantVacancy;
import com.talentpipe.application.service.ApplicantReviewService;
import com.talentpipe.application.service.ApplicantVacancyService;
import com.talentpipe.candidate.service.CandidateCvService;
import com.talentpipe.security.Permission;
import com.talentpipe.security.RequireCompanyMember;
import com.talentpipe.security.RequirePermission;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The applicants area: the vacancies to review, and one application in full.
 *
 * <p>Open to any company member; what each sees is the services' rule —
 * everyone with {@code applications.view.all}, otherwise only the vacancies
 * they work on. Anything else answers 404. The tenant always comes from the
 * access token.</p>
 */
@RestController
public class ApplicantReviewController {

    private static final String PDF = "application/pdf";

    private final ApplicantVacancyService vacancies;
    private final ApplicantReviewService reviews;

    public ApplicantReviewController(ApplicantVacancyService vacancies, ApplicantReviewService reviews) {
        this.vacancies = vacancies;
        this.reviews = reviews;
    }

    /**
     * The vacancies whose applicants the caller may review, with their counts.
     *
     * @param status published (default) or closed; archived vacancies are never listed
     */
    @RequireCompanyMember
    @GetMapping("/api/v1/applicants/vacancies")
    public List<ApplicantVacancy> vacancies(Authentication authentication,
                                            @RequestParam(required = false) String status) {
        return vacancies.list(ApplicantViewers.of(authentication), status);
    }

    /** One application: the candidate, the score and why, the CV, the stages and the history. */
    @RequireCompanyMember
    @GetMapping("/api/v1/applications/{applicationId}/review")
    public ApplicantReview review(Authentication authentication, @PathVariable UUID applicationId) {
        return reviews.review(ApplicantViewers.of(authentication), applicationId);
    }

    /**
     * Sends an applicant waiting for manual review to the vacancy's first
     * interview stage. Only from manual review: auto-advanced applicants are
     * there already, and rejected ones stay rejected.
     *
     * @return the application as it now stands
     */
    @RequirePermission(Permission.Keys.APPLICATIONS_MANAGE)
    @PostMapping("/api/v1/applications/{applicationId}/advance-to-interview")
    public ApplicantReview advanceToInterview(Authentication authentication, @PathVariable UUID applicationId,
                                              @Valid @RequestBody AdvanceToInterviewRequest request) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return reviews.advanceToInterview(ApplicantViewers.of(authentication), principal.id(), applicationId,
                request.version());
    }

    /**
     * The CV the candidate applied with.
     *
     * @param disposition {@code inline} to show it in the page — PDFs only, anything else
     *                    is always downloaded — or {@code attachment} (default)
     */
    @RequireCompanyMember
    @GetMapping("/api/v1/applications/{applicationId}/cv")
    public ResponseEntity<Resource> cv(Authentication authentication, @PathVariable UUID applicationId,
                                       @RequestParam(defaultValue = "attachment") String disposition) {
        CandidateCvService.CvDownloadPayload payload = reviews.cv(ApplicantViewers.of(authentication), applicationId);
        boolean inline = "inline".equalsIgnoreCase(disposition) && PDF.equalsIgnoreCase(payload.mimeType());
        ContentDisposition contentDisposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                // The browser must not reinterpret a CV as something it would run.
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore().cachePrivate())
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .contentLength(payload.data().length)
                .body(new ByteArrayResource(payload.data()));
    }
}
