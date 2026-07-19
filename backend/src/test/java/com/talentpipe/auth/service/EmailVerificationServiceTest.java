package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.EmailVerificationToken;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.EmailVerificationTokenRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.notification.EmailService;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link EmailVerificationService}:
 * token issuance, account activation on verify, and all rejection paths.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;

    private EmailVerificationService service() {
        return new EmailVerificationService(
                tokenRepository, userRepository, emailService, "http://localhost:5173");
    }

    private static User pendingUser() {
        Role role = org.mockito.Mockito.mock(Role.class);
        when(role.getName()).thenReturn(RoleName.COMPANY_ADMIN);
        User user = new User(UUID.randomUUID(), role, "ada@acme.io",
                "$2a$12$hash", "Ada", "Lovelace", UserStatus.PENDING_VERIFICATION);
        // Set id via reflection (BaseEntity @UuidGenerator doesn't fire outside JPA)
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    // ------------------------------------------------------- issueAndSend

    @Test
    void issueAndSend_persistsHashedToken_andSendsEmail() {
        User user = pendingUser();
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().issueAndSend(user);

        // Verify old tokens are cleared.
        verify(tokenRepository).deleteAllByUserId(user.getId());

        // Verify a token row with a non-blank hash is saved.
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(saved.getValue().getTokenHash()).isNotBlank();
        assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());

        // Verify email was dispatched with a link containing the correct base URL.
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(eq("ada@acme.io"), linkCaptor.capture());
        assertThat(linkCaptor.getValue()).startsWith("http://localhost:5173/verify-email?token=");
    }

    @Test
    void issueAndSend_rawTokenIsNeverStored() {
        User user = pendingUser();
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service().issueAndSend(user);

        // The stored hash must differ from a simple plaintext match.
        ArgumentCaptor<EmailVerificationToken> saved = ArgumentCaptor.forClass(EmailVerificationToken.class);
        verify(tokenRepository).save(saved.capture());
        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(anyString(), link.capture());

        String rawToken = link.getValue().substring(link.getValue().indexOf("token=") + 6);
        // The stored hash must be the SHA-256 of the raw token, not the raw token itself.
        assertThat(saved.getValue().getTokenHash()).isEqualTo(sha256(rawToken));
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken);
    }

    // ------------------------------------------------------- verify

    @Test
    void verify_validToken_activatesUser() {
        User user = pendingUser();
        // Simulate issuing a token to capture the hash.
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service().issueAndSend(user);
        ArgumentCaptor<String> linkCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendVerificationEmail(anyString(), linkCaptor.capture());
        String rawToken = linkCaptor.getValue().substring(linkCaptor.getValue().indexOf("token=") + 6);

        // Build the stored token object.
        EmailVerificationToken stored = new EmailVerificationToken(
                user.getId(), sha256(rawToken), Instant.now().plusSeconds(3600));
        when(tokenRepository.findByTokenHash(sha256(rawToken))).thenReturn(Optional.of(stored));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        service().verify(rawToken);

        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(stored.isUsed()).isTrue();
    }

    @Test
    void verify_unknownToken_throwsInvalidToken() {
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().verify("unknown-token"))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void verify_expiredToken_throwsInvalidToken() {
        EmailVerificationToken expired = new EmailVerificationToken(
                UUID.randomUUID(), sha256("some-token"), Instant.now().minusSeconds(1));
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service().verify("some-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void verify_alreadyUsedToken_throwsInvalidToken() {
        EmailVerificationToken token = new EmailVerificationToken(
                UUID.randomUUID(), sha256("some-token"), Instant.now().plusSeconds(3600));
        token.markUsed(Instant.now());
        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service().verify("some-token"))
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
