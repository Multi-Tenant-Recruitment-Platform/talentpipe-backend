package com.talentpipe.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.common.exception.InvalidTokenException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JwtTokenProvider}: issuance, validation, expiry,
 * tampering, token-type confusion and the unverified tenant peek.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-0123456789-0123456789-0123456789";

    private final UUID userId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    private JwtTokenProvider provider(Duration accessTtl, Duration refreshTtl) {
        return new JwtTokenProvider(
                new JwtProperties(SECRET, accessTtl, refreshTtl), new ObjectMapper());
    }

    private JwtTokenProvider defaultProvider() {
        return provider(Duration.ofMinutes(15), Duration.ofDays(7));
    }

    @Test
    void issuedAccessToken_roundTripsAllClaims() {
        JwtTokenProvider provider = defaultProvider();

        String token = provider.issueAccessToken(userId, tenantId, "COMPANY_ADMIN", "ada@acme.io");
        UserPrincipal principal = provider.parseAccessToken(token);

        assertThat(principal.id()).isEqualTo(userId);
        assertThat(principal.tenantId()).isEqualTo(tenantId);
        assertThat(principal.role()).isEqualTo("COMPANY_ADMIN");
        assertThat(principal.email()).isEqualTo("ada@acme.io");
    }

    @Test
    void accessToken_forSuperAdmin_omitsTenantClaim() {
        JwtTokenProvider provider = defaultProvider();

        String token = provider.issueAccessToken(userId, null, "SUPER_ADMIN", "ops@talentpipe.io");

        assertThat(provider.parseAccessToken(token).tenantId()).isNull();
        assertThat(provider.peekTenantId(token)).isEmpty();
    }

    @Test
    void expiredAccessToken_isRejected() {
        // Negative TTL ⇒ the token is already expired at issuance: no sleeping.
        JwtTokenProvider expiredIssuer = provider(Duration.ofSeconds(-10), Duration.ofDays(7));

        String token = expiredIssuer.issueAccessToken(userId, tenantId, "HR_MANAGER", "a@b.io");

        assertThatThrownBy(() -> expiredIssuer.parseAccessToken(token))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedSignature_isRejected() {
        JwtTokenProvider provider = defaultProvider();
        String token = provider.issueAccessToken(userId, tenantId, "COMPANY_ADMIN", "a@b.io");

        // Flip the last signature character to a guaranteed-different one.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        assertThatThrownBy(() -> provider.parseAccessToken(tampered))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tamperedPayload_isRejected() {
        JwtTokenProvider provider = defaultProvider();
        String token = provider.issueAccessToken(userId, tenantId, "INTERVIEWER", "a@b.io");

        // Splice the payload of a second token (different tenant) onto the
        // first token's signature — a classic claim-swap attempt.
        String otherToken = provider.issueAccessToken(userId, UUID.randomUUID(), "COMPANY_ADMIN", "a@b.io");
        String[] victim = token.split("\\.");
        String[] attacker = otherToken.split("\\.");
        String spliced = victim[0] + "." + attacker[1] + "." + victim[2];

        assertThatThrownBy(() -> provider.parseAccessToken(spliced))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void tokenSignedWithDifferentKey_isRejected() {
        JwtTokenProvider provider = defaultProvider();
        JwtTokenProvider rogue = new JwtTokenProvider(
                new JwtProperties("another-secret-0123456789-0123456789-01234567",
                        Duration.ofMinutes(15), Duration.ofDays(7)),
                new ObjectMapper());

        String forged = rogue.issueAccessToken(userId, tenantId, "SUPER_ADMIN", "a@b.io");

        assertThatThrownBy(() -> provider.parseAccessToken(forged))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshToken_cannotBeUsedAsAccessToken_andViceVersa() {
        JwtTokenProvider provider = defaultProvider();

        String refreshToken = provider.issueRefreshToken(userId);
        String accessToken = provider.issueAccessToken(userId, tenantId, "COMPANY_ADMIN", "a@b.io");

        assertThatThrownBy(() -> provider.parseAccessToken(refreshToken))
                .isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> provider.parseRefreshToken(accessToken))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void refreshToken_roundTripsUserId_andIsUniquePerIssuance() {
        JwtTokenProvider provider = defaultProvider();

        String first = provider.issueRefreshToken(userId);
        String second = provider.issueRefreshToken(userId);

        assertThat(provider.parseRefreshToken(first)).isEqualTo(userId);
        // jti must make concurrent sessions distinct even within one second.
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void peekTenantId_extractsClaimWithoutValidation_butNeverThrows() {
        JwtTokenProvider provider = defaultProvider();
        String token = provider.issueAccessToken(userId, tenantId, "HR_MANAGER", "a@b.io");

        assertThat(provider.peekTenantId(token)).isEqualTo(Optional.of(tenantId));
        assertThat(provider.peekTenantId("not-a-jwt")).isEmpty();
        assertThat(provider.peekTenantId("a.b.c")).isEmpty();
    }

    @Test
    void weakSecret_failsFastAtConstruction() {
        assertThatThrownBy(() -> new JwtTokenProvider(
                new JwtProperties("too-short", Duration.ofMinutes(15), Duration.ofDays(7)),
                new ObjectMapper()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
