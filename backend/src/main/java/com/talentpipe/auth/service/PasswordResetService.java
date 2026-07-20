package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.PasswordResetToken;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.PasswordResetTokenRepository;
import com.talentpipe.auth.repository.RefreshTokenRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.util.SecureTokens;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset (PB-008), covering both identities.
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>The request endpoint is a silent no-op for unknown emails and always
 *       answers 200 — it can never be used to probe for accounts.</li>
 *   <li>Tokens are 32 random bytes stored only as SHA-256 hashes, single-use,
 *       with a 30-minute TTL (PB-008 acceptance criterion).</li>
 *   <li>A successful reset revokes every active refresh token, so sessions
 *       opened with the old password die immediately.</li>
 * </ul>
 *
 * <p>An email may identify accounts in several tenants (users are unique per
 * tenant, not globally). Every match gets its own token and its own email, so
 * the recipient picks the account by following the right link — no guessing
 * which one the platform decided to reset.</p>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final CandidateAuthService candidateAuthService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final String frontendBaseUrl;

    public PasswordResetService(PasswordResetTokenRepository tokenRepository,
                                UserRepository userRepository,
                                RefreshTokenRepository refreshTokenRepository,
                                CandidateAuthService candidateAuthService,
                                PasswordEncoder passwordEncoder,
                                ApplicationEventPublisher events,
                                @Value("${talentpipe.app.base-url}") String frontendBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.candidateAuthService = candidateAuthService;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Starts a reset for every account using this email — the candidate
     * account and/or company accounts in any tenant. Returns normally whatever
     * is found, including nothing at all.
     */
    @Transactional
    public void forgotPassword(String email) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        int issued = 0;

        Optional<CandidateProfile> candidate = candidateAuthService.findByEmail(normalizedEmail);
        if (candidate.isPresent()) {
            issueToken(candidate.get().id(), null, null, normalizedEmail, candidate.get().firstName());
            issued++;
        }

        List<User> users = userRepository.findAllByEmail(normalizedEmail);
        for (User user : users) {
            issueToken(user.getId(), user.getTenantId(), user.getId(), user.getEmail(), user.getFirstName());
            issued++;
        }

        // Count only — never log which addresses do or do not exist.
        log.info("Password reset requested: {} token(s) issued", issued);
    }

    /**
     * Completes a reset: validates the token, sets a new bcrypt hash, and
     * revokes all refresh tokens for the account.
     *
     * @throws InvalidTokenException if the token is unknown, expired or used (401)
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(SecureTokens.sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown or invalid password reset token"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new InvalidTokenException("Password reset token has expired");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }
        token.markUsed(now);

        UUID ownerId = token.getUserId();

        // The owner id is either a candidate or a company user — the two live
        // in different tables and share this token table (see ADR-4).
        Optional<User> userOpt = userRepository.findById(ownerId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            // A completed reset proves ownership: clear any brute-force lock.
            user.registerSuccessfulLogin();
            refreshTokenRepository.revokeAllActiveForUser(ownerId, now);
            log.info("Password reset completed for user {} - all sessions revoked", ownerId);
            return;
        }

        if (candidateAuthService.exists(ownerId)) {
            candidateAuthService.updatePassword(ownerId, newPassword);
            refreshTokenRepository.revokeAllActiveForUser(ownerId, now);
            log.info("Password reset completed for candidate {} - all sessions revoked", ownerId);
            return;
        }

        throw new InvalidTokenException("Account no longer exists");
    }

    // ------------------------------------------------------------------ util

    /**
     * Issues one reset token and requests its email.
     *
     * @param ownerId    the account the token resets (user OR candidate id)
     * @param tenantId   tenant for the notification record, {@code null} for candidates
     * @param userId     users-row id for the notification record, {@code null} for candidates
     */
    private void issueToken(UUID ownerId, UUID tenantId, UUID userId, String recipient, String firstName) {
        tokenRepository.deleteAllByUserId(ownerId);

        String rawToken = SecureTokens.generate();
        tokenRepository.save(new PasswordResetToken(
                ownerId, SecureTokens.sha256(rawToken), Instant.now().plus(TOKEN_TTL)));

        String link = frontendBaseUrl + "/reset-password?token=" + rawToken;
        events.publishEvent(userId == null
                ? NotificationRequestedEvent.forCandidate(
                        NotificationType.PASSWORD_RESET, recipient, link, firstName)
                : NotificationRequestedEvent.forUser(
                        NotificationType.PASSWORD_RESET, tenantId, userId, recipient, link, firstName));
    }
}
