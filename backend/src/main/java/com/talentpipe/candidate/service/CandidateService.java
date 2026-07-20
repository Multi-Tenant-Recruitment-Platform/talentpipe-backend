package com.talentpipe.candidate.service;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.dto.CandidateRegisterRequest;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidate lifecycle operations (PB-006).
 *
 * <p>Candidates are tenant-independent with a globally unique email — a
 * separate identity from the tenant-scoped {@code users} table, never merged
 * with it.</p>
 */
@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateVerificationService verificationService;
    private final PasswordEncoder passwordEncoder;

    public CandidateService(CandidateRepository candidateRepository,
                            CandidateVerificationService verificationService,
                            PasswordEncoder passwordEncoder) {
        this.candidateRepository = candidateRepository;
        this.verificationService = verificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Public self-registration. The account starts PENDING_VERIFICATION and
     * cannot log in until the emailed link is followed.
     *
     * @throws DuplicateResourceException if the email is already registered (409)
     */
    @Transactional
    public CandidateProfile register(CandidateRegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (candidateRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        Candidate candidate = candidateRepository.save(new Candidate(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.identityCardNumber().trim(),
                request.address().trim(),
                request.contactNumber().trim(),
                UserStatus.PENDING_VERIFICATION));

        verificationService.issueAndSend(candidate);

        // Id only — the email address is PII and stays out of the logs.
        log.info("Registered candidate {} - verification email queued", candidate.getId());

        return CandidateAuthService.toProfile(candidate);
    }
}
