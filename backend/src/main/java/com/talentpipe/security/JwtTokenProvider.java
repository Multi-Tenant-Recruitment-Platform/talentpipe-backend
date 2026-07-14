package com.talentpipe.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.common.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * Issues and validates the platform's JWTs (JJWT, HS256).
 *
 * <p>Two token types are produced, distinguished by the {@code token_type}
 * claim so one can never be replayed as the other:</p>
 * <ul>
 *   <li><strong>ACCESS</strong> (TTL 15 min) — claims: {@code sub} = user id,
 *       {@code tenant_id} (omitted for SUPER_ADMIN), {@code role},
 *       {@code email}, {@code iat}, {@code exp}.</li>
 *   <li><strong>REFRESH</strong> (TTL 7 days) — claims: {@code sub} = user id,
 *       {@code jti} (guarantees uniqueness per issuance), {@code iat},
 *       {@code exp}. Persisted server-side as a SHA-256 hash and rotated on
 *       every use.</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    /** Minimum HS256 key length in bytes (RFC 7518: key size >= hash size). */
    private static final int MIN_SECRET_BYTES = 32;

    static final String CLAIM_TENANT_ID = "tenant_id";
    static final String CLAIM_ROLE = "role";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_TOKEN_TYPE = "token_type";
    static final String TOKEN_TYPE_ACCESS = "ACCESS";
    static final String TOKEN_TYPE_REFRESH = "REFRESH";

    private final SecretKey signingKey;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final ObjectMapper objectMapper;

    public JwtTokenProvider(JwtProperties properties, ObjectMapper objectMapper) {
        byte[] secretBytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            // Fail fast at startup — a weak signing key must never go unnoticed.
            throw new IllegalStateException(
                    "JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes for HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------- issuance

    /**
     * Issues a signed ACCESS token for the given verified identity.
     *
     * @param tenantId owning tenant; pass {@code null} for SUPER_ADMIN — the
     *                 claim is omitted entirely in that case
     */
    public String issueAccessToken(UUID userId, UUID tenantId, String role, String email) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(userId.toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_ROLE, role)
                .claim(CLAIM_EMAIL, email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)));
        if (tenantId != null) {
            builder.claim(CLAIM_TENANT_ID, tenantId.toString());
        }
        return builder.signWith(signingKey).compact();
    }

    /**
     * Issues a signed REFRESH token. A random {@code jti} makes every issued
     * token unique even for the same user within the same second, so the
     * stored hash can never collide across sessions.
     */
    public String issueRefreshToken(UUID userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_TOKEN_TYPE, TOKEN_TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(signingKey)
                .compact();
    }

    // ----------------------------------------------------------- validation

    /**
     * Verifies signature, expiry and token type of an ACCESS token.
     *
     * @return the verified identity claims
     * @throws InvalidTokenException if the token is tampered with, expired,
     *                               malformed or not an ACCESS token
     */
    public UserPrincipal parseAccessToken(String token) {
        Claims claims = verify(token, TOKEN_TYPE_ACCESS);
        String tenantId = claims.get(CLAIM_TENANT_ID, String.class);
        return new UserPrincipal(
                UUID.fromString(claims.getSubject()),
                tenantId == null ? null : UUID.fromString(tenantId),
                claims.get(CLAIM_ROLE, String.class),
                claims.get(CLAIM_EMAIL, String.class));
    }

    /**
     * Verifies signature, expiry and token type of a REFRESH token.
     *
     * @return the user id ({@code sub}) the token was issued to
     * @throws InvalidTokenException if the token is tampered with, expired,
     *                               malformed or not a REFRESH token
     */
    public UUID parseRefreshToken(String token) {
        Claims claims = verify(token, TOKEN_TYPE_REFRESH);
        return UUID.fromString(claims.getSubject());
    }

    /**
     * Extracts the {@code tenant_id} claim WITHOUT verifying the signature.
     *
     * <p>Used exclusively by {@code TenantResolvingFilter}, which by design
     * runs before full JWT validation. The value is provisional: if the token
     * subsequently fails validation, {@code JwtAuthenticationFilter} rejects
     * the request and clears the context — an unverified claim can therefore
     * never reach business code on an authenticated request. NEVER use this
     * method for authorization decisions.</p>
     */
    public Optional<UUID> peekTenantId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode tenantClaim = objectMapper.readTree(payload).get(CLAIM_TENANT_ID);
            if (tenantClaim == null || !tenantClaim.isTextual()) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(tenantClaim.asText()));
        } catch (Exception ex) {
            // Malformed token — the authentication filter will reject it.
            return Optional.empty();
        }
    }

    /** Access token lifetime — surfaced to clients as {@code expiresIn} (seconds). */
    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /** Refresh token lifetime — used to compute the persisted {@code expires_at}. */
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    private Claims verify(String token, String expectedType) {
        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Token rejected: " + ex.getClass().getSimpleName(), ex);
        }
        if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
            throw new InvalidTokenException("Unexpected token type");
        }
        return claims;
    }
}
