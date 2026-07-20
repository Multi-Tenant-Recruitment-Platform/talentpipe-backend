package com.talentpipe.candidate.service;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.entity.CandidateVerificationToken;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.candidate.repository.CandidateVerificationTokenRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.util.SecureTokens;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification for candidates.
 *
 * <p>Lives in the candidate module because it touches candidate rows; the auth
 * module reaches it only through {@link #verify(String)}, never through the
 * candidate repositories. Token rules match the company-user flow exactly:
 * 32 random bytes, stored as a SHA-256 hash, single-use, 24-hour expiry.</p>
 */
@Service
public class CandidateVerificationService {

    private static final Logger log = LoggerFactory.getLogger(CandidateVerificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final CandidateVerificationTokenRepository tokenRepository;
    private final CandidateRepository candidateRepository;
    private final ApplicationEventPublisher events;
    private final String frontendBaseUrl;

    public CandidateVerificationService(CandidateVerificationTokenRepository tokenRepository,
                                        CandidateRepository candidateRepository,
                                        ApplicationEventPublisher events,
                                        @Value("${talentpipe.app.base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.candidateRepository = candidateRepository;
        this.events = events;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Issues a fresh token and requests the activation email. Any outstanding
     * token is deleted first, so only one is ever valid.
     *
     * <p>Runs in the caller's transaction; the email is published as an event
     * and only leaves the system after that transaction commits.</p>
     */
    @Transactional
    public void issueAndSend(Candidate candidate) {
        tokenRepository.deleteAllByCandidateId(candidate.getId());

        String rawToken = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        tokenRepository.save(new CandidateVerificationToken(
                candidate.getId(), SecureTokens.sha256(rawToken), expiresAt));

        // Candidates are tenant-independent, so this attempt is not recordable
        // in the tenant-scoped notifications table (see ADR-5).
        events.publishEvent(NotificationRequestedEvent.forCandidate(
                NotificationType.EMAIL_VERIFICATION,
                candidate.getEmail(),
                frontendBaseUrl + "/verify-email?token=" + rawToken,
                candidate.getFullName()));

        log.info("Issued verification token for candidate {} (expires {})", candidate.getId(), expiresAt);
    }

    /** Re-sends verification for a candidate that is still pending. */
    @Transactional
    public void resend(UUID candidateId) {
        candidateRepository.findById(candidateId)
                .filter(candidate -> candidate.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueAndSend);
    }

    /**
     * Consumes a candidate verification token and activates the account.
     *
     * @return {@code false} when the token is not a candidate token at all, so
     *         the caller can try another token type. Tokens that ARE ours but
     *         are expired or already used throw — those are real failures.
     * @throws InvalidTokenException if the token is expired or already used
     */
    @Transactional
    public boolean verify(String rawToken) {
        var tokenOpt = tokenRepository.findByTokenHash(SecureTokens.sha256(rawToken));
        if (tokenOpt.isEmpty()) {
            return false;
        }

        CandidateVerificationToken token = tokenOpt.get();
        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new InvalidTokenException("Verification token has expired");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("Verification token has already been used");
        }
        token.markUsed(now);

        Candidate candidate = candidateRepository.findById(token.getCandidateId())
                .orElseThrow(() -> new InvalidTokenException("Candidate no longer exists"));

        if (candidate.getStatus() == UserStatus.PENDING_VERIFICATION) {
            candidate.setStatus(UserStatus.ACTIVE);
            log.info("Email verified - candidate {} activated", candidate.getId());
        }
        return true;
    }
}
