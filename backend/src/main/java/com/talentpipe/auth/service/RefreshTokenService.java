package com.talentpipe.auth.service;

import com.talentpipe.auth.entity.RefreshToken;
import com.talentpipe.auth.repository.RefreshTokenRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.security.JwtTokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the server-side lifecycle of refresh tokens: issuance (persist the
 * hash), validation, rotation and revocation.
 *
 * <p>Storage rule: the raw token exists only in transit and in the client;
 * the database sees a SHA-256 hash exclusively. SHA-256 (not bcrypt) is the
 * right tool here — the input is a high-entropy 256-bit-signed JWT, not a
 * guessable password, and lookups must be by exact hash.</p>
 */
@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
                               JwtTokenProvider jwtTokenProvider) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /** Issues a new refresh token for the user and persists its hash. */
    @Transactional
    public String issue(UUID userId) {
        String rawToken = jwtTokenProvider.issueRefreshToken(userId);
        Instant expiresAt = Instant.now().plus(jwtTokenProvider.getRefreshTokenTtl());
        refreshTokenRepository.save(new RefreshToken(userId, sha256(rawToken), expiresAt));
        return rawToken;
    }

    /**
     * Validates a presented refresh token and rotates it: the old record is
     * revoked and a fresh token is issued in one transaction, so a token can
     * never be used twice.
     *
     * @return the user id the (now consumed) token belonged to
     * @throws InvalidTokenException on any failure — signature, expiry,
     *                               unknown hash, already revoked (401)
     */
    @Transactional
    public UUID validateAndConsume(String rawToken) {
        // 1. Cryptographic validation (signature, expiry, REFRESH type).
        UUID userId = jwtTokenProvider.parseRefreshToken(rawToken);

        // 2. Server-side state validation against the stored hash.
        RefreshToken stored = refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Unknown refresh token"));
        Instant now = Instant.now();
        if (stored.isRevoked() || stored.isExpired(now)) {
            throw new InvalidTokenException("Refresh token revoked or expired");
        }

        // 3. Consume: rotation revokes the old token before a new one is issued.
        stored.revoke(now);
        return userId;
    }

    /**
     * Revokes a token at logout. Idempotent and ownership-checked: a caller
     * can only revoke tokens issued to themselves; anything else is a no-op
     * (revealing nothing about other users' tokens).
     */
    @Transactional
    public void revoke(String rawToken, UUID ownerUserId) {
        refreshTokenRepository.findByTokenHash(sha256(rawToken))
                .filter(token -> token.getUserId().equals(ownerUserId))
                .ifPresent(token -> token.revoke(Instant.now()));
    }

    private static String sha256(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is mandated by the JCA spec — unreachable on a compliant JVM.
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
