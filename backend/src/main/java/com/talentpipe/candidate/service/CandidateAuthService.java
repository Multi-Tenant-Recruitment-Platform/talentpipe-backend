package com.talentpipe.candidate.service;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.AccountLockedException;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The candidate module's authentication API — the seam the auth module talks
 * to instead of reaching into candidate repositories.
 *
 * <p>Every password comparison happens inside this class, so the candidate
 * password hash never crosses a module boundary. Callers receive
 * {@link CandidateProfile} only.</p>
 */
@Service
public class CandidateAuthService {

    private static final Logger log = LoggerFactory.getLogger(CandidateAuthService.class);

    private final CandidateRepository candidateRepository;
    private final CandidateVerificationService verificationService;
    private final CandidateLoginAttemptService loginAttempts;
    private final PasswordEncoder passwordEncoder;

    public CandidateAuthService(CandidateRepository candidateRepository,
                                CandidateVerificationService verificationService,
                                CandidateLoginAttemptService loginAttempts,
                                PasswordEncoder passwordEncoder) {
        this.candidateRepository = candidateRepository;
        this.verificationService = verificationService;
        this.loginAttempts = loginAttempts;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verifies candidate credentials, applying lockout and verification gating.
     *
     * @return the profile on success; {@link Optional#empty()} when this email
     *         is not a candidate at all OR the password is wrong — the caller
     *         turns both into the same generic 401
     * @throws AccountLockedException      if the account is temporarily locked
     * @throws AccountNotVerifiedException if the email has not been verified
     */
    @Transactional
    public Optional<CandidateProfile> authenticate(String email, String rawPassword) {
        Optional<Candidate> candidateOpt = candidateRepository.findByEmail(normalizeEmail(email));
        if (candidateOpt.isEmpty()) {
            return Optional.empty();
        }

        Candidate candidate = candidateOpt.get();
        Instant now = Instant.now();

        // Lock is checked before the password: a locked account must stay
        // locked even for the correct password, or the lock means nothing.
        if (candidate.isLocked(now)) {
            throw new AccountLockedException(candidate.getLockedUntil(), now);
        }

        if (!passwordEncoder.matches(rawPassword, candidate.getPasswordHash())) {
            // Own transaction: the outer login transaction rolls back when it
            // reports "invalid credentials", which would otherwise discard this
            // increment and defeat the lock.
            loginAttempts.recordFailure(candidate.getId());
            return Optional.empty();
        }

        if (candidate.getStatus() == UserStatus.PENDING_VERIFICATION) {
            // Credentials were correct, so naming the real reason leaks nothing.
            throw new AccountNotVerifiedException(
                    "Your email address is not verified yet. Check your inbox for the "
                            + "verification link, or request a new one.");
        }
        if (candidate.getStatus() != UserStatus.ACTIVE) {
            return Optional.empty(); // DISABLED — stays a generic 401
        }

        loginAttempts.recordSuccess(candidate.getId());
        return Optional.of(toProfile(candidate));
    }

    /** Profile lookup by id — used for token refresh and {@code /auth/me}. */
    @Transactional(readOnly = true)
    public Optional<CandidateProfile> findById(UUID candidateId) {
        return candidateRepository.findById(candidateId).map(CandidateAuthService::toProfile);
    }

    /** Profile lookup by email — used by the global password-reset flow. */
    @Transactional(readOnly = true)
    public Optional<CandidateProfile> findByEmail(String email) {
        return candidateRepository.findByEmail(normalizeEmail(email)).map(CandidateAuthService::toProfile);
    }

    /** True when the id belongs to a candidate rather than a company user. */
    @Transactional(readOnly = true)
    public boolean exists(UUID candidateId) {
        return candidateRepository.existsById(candidateId);
    }

    /**
     * Sets a new password (hashing happens here, inside the module) and clears
     * any lockout — a successful reset is proof of ownership.
     */
    @Transactional
    public void updatePassword(UUID candidateId, String rawPassword) {
        candidateRepository.findById(candidateId).ifPresent(candidate -> {
            candidate.setPasswordHash(passwordEncoder.encode(rawPassword));
            candidate.registerSuccessfulLogin();
            log.info("Password updated for candidate {}", candidateId);
        });
    }

    /** Re-issues a verification email if the candidate is still pending. */
    @Transactional
    public void resendVerification(String email) {
        candidateRepository.findByEmail(normalizeEmail(email))
                .filter(candidate -> candidate.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(verificationService::issueAndSend);
    }

    // ------------------------------------------------------------------ util

    /** Splits a full name once, so every consumer renders it identically. */
    static CandidateProfile toProfile(Candidate candidate) {
        String fullName = candidate.getFullName();
        String firstName = fullName;
        String lastName = "";
        int lastSpace = fullName.lastIndexOf(' ');
        if (lastSpace > 0) {
            firstName = fullName.substring(0, lastSpace).trim();
            lastName = fullName.substring(lastSpace).trim();
        }
        return new CandidateProfile(
                candidate.getId(),
                candidate.getEmail(),
                firstName,
                lastName,
                candidate.getStatus().name(),
                candidate.getCreatedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
