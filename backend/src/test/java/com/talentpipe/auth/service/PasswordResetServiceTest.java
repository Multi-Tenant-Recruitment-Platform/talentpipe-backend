package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.PasswordResetToken;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
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
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link PasswordResetService}:
 * no-op for unknown user/tenant, token issuance, confirmation (password
 * update + session revocation), and all rejection paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private com.talentpipe.candidate.repository.CandidateRepository candidateRepository;
    @Mock private TenantService tenantService;
    @Mock private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private PasswordResetService service() {
        return new PasswordResetService(
                tokenRepository, userRepository, refreshTokenRepository, candidateRepository,
                tenantService, passwordEncoder, emailService, "http://localhost:5173");
    }

    private static User activeUser(UUID tenantId) {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getName()).thenReturn(RoleName.COMPANY_ADMIN);
        User user = new User(tenantId, role, "ada@acme.io",
                "$2a$04$hash", "Ada", "Lovelace", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static TenantResponse tenantResponse(UUID tenantId) {
        return new TenantResponse(tenantId, "Acme Inc", "acme",
                null, "STANDARD", "ACTIVE", Instant.now());
    }

    // ------------------------------------------------------- requestReset

    @Test
    void requestReset_knownUser_issuesTokenAndSendsEmail() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.findBySubdomain("acme")).thenReturn(Optional.of(tenantResponse(tenantId)));
        User user = activeUser(tenantId);
        when(userRepository.findByTenantIdAndEmail(tenantId, "ada@acme.io"))
                .thenReturn(Optional.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().requestReset("ada@acme.io", "acme");

        verify(tokenRepository).deleteAllByUserId(user.getId());
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));

        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(eq("ada@acme.io"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:5173/reset-password?token=");
    }

    @Test
    void requestReset_unknownSubdomain_isNoOp() {
        when(tenantService.findBySubdomain("unknown")).thenReturn(Optional.empty());

        // Must NOT throw — no enumeration.
        service().requestReset("ada@acme.io", "unknown");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestReset_unknownEmail_isNoOp() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.findBySubdomain("acme")).thenReturn(Optional.of(tenantResponse(tenantId)));
        when(userRepository.findByTenantIdAndEmail(eq(tenantId), anyString()))
                .thenReturn(Optional.empty());

        // Must NOT throw — no enumeration.
        service().requestReset("nobody@acme.io", "acme");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void requestReset_normalizesEmail() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.findBySubdomain("acme")).thenReturn(Optional.of(tenantResponse(tenantId)));
        when(userRepository.findByTenantIdAndEmail(tenantId, "ada@acme.io"))
                .thenReturn(Optional.empty()); // will be a no-op, that's fine

        service().requestReset("  ADA@ACME.IO  ", "acme");

        // The call must use the normalized email.
        verify(userRepository).findByTenantIdAndEmail(tenantId, "ada@acme.io");
    }

    // ------------------------------------------------------- confirmReset

    @Test
    void confirmReset_validToken_updatesPasswordAndRevokesTokens() {
        UUID userId = UUID.randomUUID();
        String rawToken = "test-raw-token";
        PasswordResetToken stored = new PasswordResetToken(userId, sha256(rawToken),
                Instant.now().plusSeconds(1800));
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(stored));

        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getName()).thenReturn(RoleName.COMPANY_ADMIN);
        User user = new User(UUID.randomUUID(), role, "ada@acme.io",
                "$2a$04$oldhash", "Ada", "Lovelace", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        // revokeAllActiveForUser returns void; no mock setup needed.

        service().confirmReset(rawToken, "new-s3cret-password");

        // Password must be changed and bcrypt-hashed.
        assertThat(user.getPasswordHash()).isNotEqualTo("$2a$04$oldhash");
        assertThat(passwordEncoder.matches("new-s3cret-password", user.getPasswordHash())).isTrue();

        // Token must be consumed.
        assertThat(stored.isUsed()).isTrue();

        // All sessions must be revoked.
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(userId), any());
    }

    @Test
    void confirmReset_unknownToken_throwsInvalidToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().confirmReset("bad-token", "new-pass-word"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void confirmReset_expiredToken_throwsInvalidToken() {
        PasswordResetToken expired = new PasswordResetToken(
                UUID.randomUUID(), sha256("tok"), Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(sha256("tok"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service().confirmReset("tok", "new-pass-word"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void confirmReset_alreadyUsedToken_throwsInvalidToken() {
        PasswordResetToken used = new PasswordResetToken(
                UUID.randomUUID(), sha256("tok"), Instant.now().plusSeconds(1800));
        used.markUsed(Instant.now());
        when(tokenRepository.findByTokenHash(sha256("tok"))).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service().confirmReset("tok", "new-pass-word"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("already been used");
    }

    // ------------------------------------------------------------------ util

    private static String sha256(String input) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
