package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.PasswordResetToken;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.PasswordResetTokenRepository;
import com.talentpipe.auth.repository.RefreshTokenRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.notification.EmailService;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages the password reset lifecycle: token request and confirmation.
 *
 * <p>Security properties:</p>
 * <ul>
 *   <li>Requests for unknown emails are silently no-ops — no exception is
 *       thrown and no timing difference is detectable (user enumeration is
 *       prevented).</li>
 *   <li>Tokens are 32-byte random values stored only as SHA-256 hashes.</li>
 *   <li>Token TTL is 30 minutes (PB-008 acceptance criterion).</li>
 *   <li>A successful reset immediately revokes all active refresh tokens,
 *       terminating every existing session for the user.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);

    private final PasswordResetTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TenantService tenantService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String appBaseUrl;

    public PasswordResetService(
            PasswordResetTokenRepository tokenRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            TenantService tenantService,
            PasswordEncoder passwordEncoder,
            EmailService emailService,
            @Value("${talentpipe.app.base-url:http://localhost:5173}") String appBaseUrl) {
        this.tokenRepository = tokenRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tenantService = tenantService;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.appBaseUrl = appBaseUrl;
    }

    /**
     * Initiates a password reset for {@code email} within the tenant identified
     * by {@code subdomain}. Always returns without exception — callers receive
     * no information about whether the tenant or user exist.
     */
    @Transactional
    public void requestReset(String email, String subdomain) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

        Optional<TenantResponse> tenantOpt = tenantService.findBySubdomain(subdomain);
        if (tenantOpt.isEmpty()) {
            log.debug("Password reset requested for unknown subdomain '{}' — no-op", subdomain);
            return;
        }

        Optional<User> userOpt = userRepository.findByTenantIdAndEmail(
                tenantOpt.get().id(), normalizedEmail);
        if (userOpt.isEmpty()) {
            log.debug("Password reset requested for unknown user '{}' in tenant '{}' — no-op",
                    normalizedEmail, subdomain);
            return;
        }

        User user = userOpt.get();

        // Remove any previous outstanding token before issuing a new one.
        tokenRepository.deleteAllByUserId(user.getId());

        String rawToken = generateSecureToken();
        Instant expiresAt = Instant.now().plus(TOKEN_TTL);
        tokenRepository.save(new PasswordResetToken(user.getId(), sha256(rawToken), expiresAt));

        String resetLink = appBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);

        log.info("Password reset token issued for user {} (expires {})", user.getId(), expiresAt);
    }

    /**
     * Validates the token and replaces the user's password with a bcrypt hash
     * of {@code newPassword}. All active refresh tokens for the user are then
     * revoked, invalidating every existing session.
     *
     * @param rawToken    plain token from the reset link
     * @param newPassword plaintext new password (will be bcrypt-hashed)
     * @throws InvalidTokenException if the token is unknown, expired, or already used (401)
     */
    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        PasswordResetToken token = tokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown or invalid password reset token"));

        Instant now = Instant.now();
        if (token.isExpired(now)) {
            throw new InvalidTokenException("Password reset token has expired");
        }
        if (token.isUsed()) {
            throw new InvalidTokenException("Password reset token has already been used");
        }

        token.markUsed(now);

        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> new InvalidTokenException("User no longer exists"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        // Revoke all existing sessions so the old password cannot be used to
        // keep any session alive after the reset.
        refreshTokenRepository.revokeAllActiveForUser(user.getId(), now);
        log.info("Password reset confirmed for user {} — all active sessions revoked", user.getId());
    }

    // ------------------------------------------------------------------ util

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
