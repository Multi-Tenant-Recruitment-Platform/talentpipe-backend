package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.EmailVerificationToken;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.EmailVerificationTokenRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.notification.EmailService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the email verification lifecycle: token issuance after registration
 * and token consumption to activate a {@code PENDING_VERIFICATION} account.
 *
 * <p>Token security model: a 32-byte (256-bit) cryptographically random value
 * is URL-safe base64-encoded. Only its SHA-256 hash is persisted — the raw
 * token travels exclusively in the emailed link and is never stored.</p>
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);
    private static final Duration TOKEN_TTL = Duration.ofHours(24);

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final String appBaseUrl;

    public EmailVerificationService(
            EmailVerificationTokenRepository tokenRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Value("${talentpipe.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Generates a fresh verification token, persists its hash, and dispatches
     * the activation email. Any previously-issued token for the user is deleted
     * first so only one valid token exists at a time.
     *
     * <p>Must run inside the caller's transaction so the token row is only
     * committed if the user row was also committed.</p>
     */
    @Transactional
    public void issueAndSend(User user) {
        // Remove any outstanding token before issuing a new one.
        tokenRepository.deleteAllByUserId(user.getId());

        String rawToken = generateSecureToken();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        tokenRepository.save(new EmailVerificationToken(user.getId(), sha256(rawToken), expiresAt));

        String verificationLink = appBaseUrl + "/verify-email?token=" + rawToken;
        emailService.sendVerificationEmail(user.getEmail(), verificationLink);

        log.info("Issued email verification token for user {} (expires {})", user.getId(), expiresAt);
    }

    /**
     * Validates a raw token and, if valid, activates the user account.
     *
     * @param rawToken the plain token value from the email link
     * @throws InvalidTokenException if the token is unknown, expired, or already used (401)
     */
    @Transactional
    public void verify(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown or invalid verification token"));

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
            log.info("Email verified — user {} activated (tenantId={})", user.getId(), user.getTenantId());
        }
        // If status is already ACTIVE (re-verify edge case), we still mark the
        // token used but do not change the status — idempotent and safe.
    }

    // ------------------------------------------------------------------ util

    /** Generates a URL-safe base64-encoded 32-byte random token. */
    private static String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
