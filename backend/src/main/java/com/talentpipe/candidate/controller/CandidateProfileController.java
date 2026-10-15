package com.talentpipe.candidate.controller;

import com.talentpipe.candidate.dto.MyCandidateProfileResponse;
import com.talentpipe.candidate.dto.UpdateCandidateProfileRequest;
import com.talentpipe.candidate.service.CandidateCvService;
import com.talentpipe.candidate.service.CandidateProfileService;
import com.talentpipe.security.UserPrincipal;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller managing the authenticated candidate's own profile, photo, and CV.
 */
@RestController
@RequestMapping("/api/v1/candidates/me")
@PreAuthorize("hasRole('CANDIDATE')")
public class CandidateProfileController {

    private final CandidateProfileService profileService;
    private final CandidateCvService cvService;

    public CandidateProfileController(CandidateProfileService profileService,
                                      CandidateCvService cvService) {
        this.profileService = profileService;
        this.cvService = cvService;
    }

    /**
     * Retrieves the profile of the currently signed-in candidate.
     */
    @GetMapping
    public MyCandidateProfileResponse getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.getProfile(principal.id());
    }

    /**
     * Upserts the signed-in candidate's details, profile picture, and talent pool CV.
     */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MyCandidateProfileResponse updateMyProfile(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @ModelAttribute UpdateCandidateProfileRequest request) {
        return profileService.updateProfile(principal.id(), request);
    }

    /**
     * Downloads the active CV of the signed-in candidate securely.
     */
    @GetMapping("/cv/download")
    public ResponseEntity<Resource> downloadMyCv(@AuthenticationPrincipal UserPrincipal principal) {
        CandidateCvService.CvDownloadPayload payload = cvService.loadActiveCvResource(principal.id());

        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(payload.fileName(), StandardCharsets.UTF_8)
                .build();

        ByteArrayResource resource = new ByteArrayResource(payload.data());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(payload.mimeType()))
                .contentLength(payload.data().length)
                .body(resource);
    }
}
