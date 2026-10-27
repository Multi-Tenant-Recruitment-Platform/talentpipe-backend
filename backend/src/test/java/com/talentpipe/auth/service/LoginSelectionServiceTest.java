package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.LoginSelection;
import com.talentpipe.auth.repository.LoginSelectionRepository;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.InvalidTokenException;
import com.talentpipe.common.util.SecureTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for the single-use token behind the "choose a workspace" step. */
@ExtendWith(MockitoExtension.class)
class LoginSelectionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T09:00:00Z");

    @Mock
    private LoginSelectionRepository repository;
    @Captor
    private ArgumentCaptor<LoginSelection> saved;

    private LoginSelectionService service;

    @BeforeEach
    void setUp() {
        service = new LoginSelectionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issue_storesOnlyTheHash_validForTwoMinutes_afterPurgingExpiredChoices() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(repository.save(any(LoginSelection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String rawToken = service.issue(List.of(first, second));

        InOrder order = inOrder(repository);
        order.verify(repository).deleteExpiredBefore(NOW);
        order.verify(repository).save(saved.capture());
        LoginSelection selection = saved.getValue();
        assertThat(selection.getTokenHash()).isEqualTo(SecureTokens.sha256(rawToken)).isNotEqualTo(rawToken);
        assertThat(selection.getAccountIds()).containsExactlyInAnyOrder(first, second);
        assertThat(selection.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(service.ttlSeconds()).isEqualTo(120L);
    }

    @Test
    void redeem_returnsTheChosenAccount_andUsesTheTokenUp() {
        UUID account = UUID.randomUUID();
        LoginSelection selection = stored("raw", NOW.plusSeconds(60), account);

        assertThat(service.redeem("raw", account)).isEqualTo(account);
        assertThat(selection.isRedeemable(NOW)).isFalse();
    }

    @Test
    void redeem_refusesATokenThatWasAlreadyUsed() {
        UUID account = UUID.randomUUID();
        LoginSelection selection = stored("raw", NOW.plusSeconds(60), account);
        selection.markUsed(NOW.minusSeconds(5));

        assertThatThrownBy(() -> service.redeem("raw", account)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void redeem_refusesAnExpiredToken() {
        UUID account = UUID.randomUUID();
        stored("raw", NOW.minusSeconds(1), account);

        assertThatThrownBy(() -> service.redeem("raw", account)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void redeem_refusesAnUnknownToken() {
        UUID account = UUID.randomUUID();

        assertThatThrownBy(() -> service.redeem("made-up", account)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void redeem_refusesAnAccountThatWasNotOffered_andLeavesTheTokenUsable() {
        LoginSelection selection = stored("raw", NOW.plusSeconds(60), UUID.randomUUID());
        UUID notOffered = UUID.randomUUID();

        assertThatThrownBy(() -> service.redeem("raw", notOffered)).isInstanceOf(InvalidRequestException.class);
        assertThat(selection.isRedeemable(NOW)).isTrue();
    }

    private LoginSelection stored(String rawToken, Instant expiresAt, UUID... accounts) {
        LoginSelection selection = new LoginSelection(SecureTokens.sha256(rawToken), List.of(accounts), expiresAt);
        when(repository.findByTokenHash(SecureTokens.sha256(rawToken))).thenReturn(Optional.of(selection));
        return selection;
    }
}
