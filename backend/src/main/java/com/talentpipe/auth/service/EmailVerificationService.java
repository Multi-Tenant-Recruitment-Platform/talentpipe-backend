package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.EmailVerificationToken;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.EmailVerificationTokenRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.util.SecureTokens;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email verification for company users (PB-001): token issuance at
 * registration or invitation, and consumption to activate a
 * {@code PENDING_VERIFICATION} account.
 *
 * <p>Token security model: 32 cryptographically random bytes, URL-safe
 * base64-encoded. Only the SHA-256 hash is persisted — the raw token exists
 * only in the emailed link. Tokens are single-use and expire in 24 hours.</p>
 *
 * <p>Candidate verification is the candidate module's job
 * ({@code CandidateVerificationService}); this service never touches candidate
 * rows.</p>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher events;
    private final String frontendBaseUrl;

    public EmailVerificationService(EmailVerificationTokenRepository tokenRepository,
                                    UserRepository userRepository,
                                    ApplicationEventPublisher events,
                                    @Value("${talentpipe.app.base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.events = events;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Issues a fresh verification token and requests the activation email.
     * Any outstanding token for the user is deleted first, so exactly one is
     * ever valid.
     *
     * <p>Runs in the caller's transaction: the token row and the user row commit
     * together, and the email only leaves after that commit.</p>
     */
    @Transactional
    public void issueAndSend(User user) {
        tokenRepository.deleteAllByUserId(user.getId());

        String rawToken = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        tokenRepository.save(new EmailVerificationToken(
                user.getId(), SecureTokens.sha256(rawToken), expiresAt));

        events.publishEvent(NotificationRequestedEvent.forUser(
                NotificationType.EMAIL_VERIFICATION,
                user.getTenantId(),
                user.getId(),
                user.getEmail(),
                frontendBaseUrl + "/verify-email?token=" + rawToken,
                user.getFirstName()));

        log.info("Issued verification token for user {} (expires {})", user.getId(), expiresAt);
    }

    /**
     * Re-sends verification for a pending account (PB-001).
     *
     * <p>Silently does nothing when the account is unknown or already active:
     * the endpoint must not become an oracle for which emails are registered.</p>
     */
    @Transactional
    public void resend(java.util.UUID tenantId, String email) {
        userRepository.findByTenantIdAndEmail(tenantId, email)
                .filter(user -> user.getStatus() == UserStatus.PENDING_VERIFICATION)
                .ifPresent(this::issueAndSend);
    }

    /**
     * Consumes a verification token and activates the user account.
     *
     * @return {@code false} when the token is not a company-user token, so the
     *         caller can try the candidate flow. Tokens that ARE ours but are
     *         expired or already used throw — those are real failures.
     * @throws InvalidTokenException if the token is expired or already used
     */
    @Transactional
    public boolean verify(String rawToken) {
        var tokenOpt = tokenRepository.findByTokenHash(SecureTokens.sha256(rawToken));
        if (tokenOpt.isEmpty()) {
            return false;
        }

        EmailVerificationToken token = tokenOpt.get();
        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new InvalidTokenException("Verification token has expired");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("Verification token has already been used");
        }
        token.markUsed(now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));

        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
            log.info("Email verified - user {} activated (tenantId={})", user.getId(), user.getTenantId());
        }
        return true;
    }
}
