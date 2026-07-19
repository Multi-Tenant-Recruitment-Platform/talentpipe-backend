package com.talentpipe.candidate.service;

import com.talentpipe.auth.dto.UserResponse;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.service.EmailVerificationService;
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
 * Service managing candidate lifecycle operations (registration, profile, etc.).
 * Coordinates with the notification subsystem to dispatch verification links.
 */
@Service
public class CandidateService {

    private static final Logger log = LoggerFactory.getLogger(CandidateService.class);

    private final CandidateRepository candidateRepository;
    private final EmailVerificationService emailVerificationService;
    private final PasswordEncoder passwordEncoder;

    public CandidateService(CandidateRepository candidateRepository,
                            EmailVerificationService emailVerificationService,
                            PasswordEncoder passwordEncoder) {
        this.candidateRepository = candidateRepository;
        this.emailVerificationService = emailVerificationService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Public self-registration (PB-006).
     * Creates a new Candidate account in PENDING_VERIFICATION status and
     * dispatches an activation email.
     *
     * @throws DuplicateResourceException if the email is already registered by another candidate.
     */
    @Transactional
    public UserResponse register(CandidateRegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (candidateRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered");
        }

        Candidate candidate = new Candidate(
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().trim(),
                request.identityCardNumber().trim(),
                request.address().trim(),
                request.contactNumber().trim(),
                UserStatus.PENDING_VERIFICATION
        );

        candidate = candidateRepository.save(candidate);

        // Dispatch verification token & email
        emailVerificationService.issueAndSend(candidate);

        log.info("Registered candidate (id={}, email={}) — verification link dispatched",
                candidate.getId(), candidate.getEmail());

        return mapCandidateToUserResponse(candidate);
    }

    private UserResponse mapCandidateToUserResponse(Candidate candidate) {
        String fullName = candidate.getFullName();
        String firstName = fullName;
        String lastName = "";
        int lastSpaceIdx = fullName.lastIndexOf(' ');
        if (lastSpaceIdx > 0) {
            firstName = fullName.substring(0, lastSpaceIdx).trim();
            lastName = fullName.substring(lastSpaceIdx).trim();
        }
        return new UserResponse(
                candidate.getId(),
                null,
                null,
                RoleName.CANDIDATE.name(),
                candidate.getEmail(),
                firstName,
                lastName,
                candidate.getStatus().name(),
                candidate.getCreatedAt()
        );
    }
}
