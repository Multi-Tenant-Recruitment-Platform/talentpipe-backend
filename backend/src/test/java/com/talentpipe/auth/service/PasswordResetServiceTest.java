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
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.notification.entity.NotificationType;
import com.talentpipe.notification.event.NotificationRequestedEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link PasswordResetService}:
 * no-op for unknown emails, token issuance, confirmation (password update +
 * session revocation), and all rejection paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private PasswordResetTokenRepository tokenRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private CandidateAuthService candidateAuthService;
    @Mock private ApplicationEventPublisher events;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private PasswordResetService service() {
        return new PasswordResetService(
                tokenRepository, userRepository, refreshTokenRepository, candidateAuthService,
                passwordEncoder, events, "http://localhost:5173");
    }

    private static User activeUser(UUID tenantId) {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getName()).thenReturn(RoleName.COMPANY_ADMIN);
        User user = new User(tenantId, role, "ada@acme.io",
                "$2a$04$hash", "Ada", "Lovelace", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    // ------------------------------------------------------ forgotPassword

    @Test
    void forgotPassword_knownUser_issuesTokenAndPublishesEmail() {
        UUID tenantId = UUID.randomUUID();
        User user = activeUser(tenantId);
        when(candidateAuthService.findByEmail("ada@acme.io")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmail("ada@acme.io")).thenReturn(List.of(user));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().forgotPassword("ada@acme.io");

        verify(tokenRepository).deleteAllByUserId(user.getId());
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plusSeconds(29 * 60), Instant.now().plusSeconds(31 * 60));

        ArgumentCaptor<NotificationRequestedEvent> event =
                ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().type()).isEqualTo(NotificationType.PASSWORD_RESET);
        assertThat(event.getValue().recipient()).isEqualTo("ada@acme.io");
        assertThat(event.getValue().link()).startsWith("http://localhost:5173/reset-password?token=");
    }

    @Test
    void forgotPassword_unknownEmail_isNoOp() {
        when(candidateAuthService.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findAllByEmail(anyString())).thenReturn(List.of());

        // Must NOT throw — no enumeration.
        service().forgotPassword("nobody@acme.io");

        verify(tokenRepository, never()).save(any());
        verify(events, never()).publishEvent(any(NotificationRequestedEvent.class));
    }

    @Test
    void forgotPassword_normalizesEmail() {
        when(candidateAuthService.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.findAllByEmail(anyString())).thenReturn(List.of());

        service().forgotPassword("  ADA@ACME.IO  ");

        // The lookup must use the normalized email.
        verify(userRepository).findAllByEmail("ada@acme.io");
    }

    @Test
    void forgotPassword_emailInMultipleTenants_issuesOneTokenPerAccount() {
        User first = activeUser(UUID.randomUUID());
        User second = activeUser(UUID.randomUUID());
        when(candidateAuthService.findByEmail("ada@acme.io")).thenReturn(Optional.empty());
        when(userRepository.findAllByEmail("ada@acme.io")).thenReturn(List.of(first, second));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().forgotPassword("ada@acme.io");

        // Every matching account gets its own token — none is silently skipped.
        verify(tokenRepository).deleteAllByUserId(first.getId());
        verify(tokenRepository).deleteAllByUserId(second.getId());
        verify(events, org.mockito.Mockito.times(2))
                .publishEvent(any(NotificationRequestedEvent.class));
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
    void confirmReset_candidateToken_delegatesToCandidateModule() {
        UUID candidateId = UUID.randomUUID();
        String rawToken = "candidate-raw-token";
        PasswordResetToken stored = new PasswordResetToken(candidateId, sha256(rawToken),
                Instant.now().plusSeconds(1800));
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(stored));
        when(userRepository.findById(candidateId)).thenReturn(Optional.empty());
        when(candidateAuthService.exists(candidateId)).thenReturn(true);

        service().confirmReset(rawToken, "new-s3cret-password");

        // Hashing stays inside the candidate module — the raw password is handed over.
        verify(candidateAuthService).updatePassword(candidateId, "new-s3cret-password");
        verify(refreshTokenRepository).revokeAllActiveForUser(eq(candidateId), any());
        assertThat(stored.isUsed()).isTrue();
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
