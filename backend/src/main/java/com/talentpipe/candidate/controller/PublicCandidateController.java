package com.talentpipe.candidate.controller;

import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.dto.CandidateRegisterRequest;
import com.talentpipe.candidate.service.CandidateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public candidate endpoints (unauthenticated).
 *
 * <p>Registration returns the candidate's own {@link CandidateProfile} and no
 * tokens: the account starts PENDING_VERIFICATION and cannot authenticate until
 * the emailed link is followed.</p>
 */
@RestController
@RequestMapping("/api/v1/public/candidates")
public class PublicCandidateController {

    private final CandidateService candidateService;

    public PublicCandidateController(CandidateService candidateService) {
        this.candidateService = candidateService;
    }

    /** Public candidate self-registration (PB-006). */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public CandidateProfile register(@Valid @RequestBody CandidateRegisterRequest request) {
        return candidateService.register(request);
    }
}
