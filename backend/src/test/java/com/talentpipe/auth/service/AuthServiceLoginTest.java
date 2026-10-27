package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.dto.AuthResponse;
import com.talentpipe.auth.dto.LoginRequest;
import com.talentpipe.auth.dto.SelectWorkspaceRequest;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.AccountType;
import com.talentpipe.auth.dto.WorkspaceSelectionResponse.Choice;
import com.talentpipe.auth.entity.SystemRole;
import com.talentpipe.auth.entity.TenantRole;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.candidate.dto.CandidateCredentialCheck;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.service.CandidateAuthService;
import com.talentpipe.common.exception.AccountLockedException;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import com.talentpipe.common.exception.ErrorCode;
import com.talentpipe.common.exception.WorkspaceSelectionRequiredException;
import com.talentpipe.security.JwtTokenProvider;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for sign-in without a workspace (what the sign-in modal sends)
 * and for choosing a workspace when the credentials fit several accounts.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

    private static final String EMAIL = "ada@acme.io";
    private static final String PASSWORD = "s3cret-password";

    @Mock
    private UserRepository userRepository;
    @Mock
    private TenantRoleService tenantRoleService;
    @Mock
    private TenantService tenantService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private CandidateAuthService candidateAuthService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private LoginSelectionService loginSelectionService;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Captor
    private ArgumentCaptor<Collection<UUID>> offered;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(userRepository, tenantRoleService, tenantService,
                refreshTokenService, emailVerificationService, candidateAuthService, loginAttemptService,
                loginSelectionService, jwtTokenProvider, passwordEncoder, new UserMapper());
    }

    // ------------------------------------------------------------ one match

    @Test
    void theOneMatchingAccount_isSignedInToDirectly() {
        User acme = member(UserStatus.ACTIVE);
        User other = member(UserStatus.ACTIVE);
        passwordFits(acme);
        passwordDoesNotFit(other);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(acme, other));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD)).thenReturn(CandidateCredentialCheck.NONE);
        expectTokens();

        AuthResponse response = service.login(null, credentials());

        assertThat(response.user().tenantId()).isEqualTo(acme.getTenantId());
        verify(loginAttemptService).recordSuccess(acme.getId());
        verify(loginAttemptService, never()).recordFailure(any());
        verifyNoInteractions(loginSelectionService);
    }

    @Test
    void withNoCompanyAccount_theCandidateSignInIsUnchanged() {
        CandidateProfile candidate = candidateProfile();
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of());
        when(candidateAuthService.authenticate(EMAIL, PASSWORD)).thenReturn(Optional.of(candidate));
        expectTokens();

        AuthResponse response = service.login(null, credentials());

        assertThat(response.user().role()).isEqualTo("CANDIDATE");
        verify(candidateAuthService, never()).checkCredentials(anyString(), anyString());
    }

    @Test
    void whenOnlyTheCandidateAccountMatches_theCandidateIsSignedIn() {
        User acme = member(UserStatus.ACTIVE);
        CandidateProfile candidate = candidateProfile();
        passwordDoesNotFit(acme);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(acme));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD))
                .thenReturn(CandidateCredentialCheck.checked(candidate.id(), true));
        when(candidateAuthService.completeSignIn(candidate.id())).thenReturn(Optional.of(candidate));
        expectTokens();

        AuthResponse response = service.login(null, credentials());

        assertThat(response.user().id()).isEqualTo(candidate.id());
        // The person typed the right password for an account, so nothing counts as a failure.
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void anAccountThatIsNotVerifiedYet_isRefusedWithTheReason() {
        User pending = member(UserStatus.PENDING_VERIFICATION);
        passwordFits(pending);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(pending));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD)).thenReturn(CandidateCredentialCheck.NONE);
        LoginRequest request = credentials();

        assertThatThrownBy(() -> service.login(null, request))
                .isInstanceOfSatisfying(AccountNotVerifiedException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.ACCOUNT_NOT_VERIFIED));
    }

    // ------------------------------------------------------- several matches

    @Test
    void severalMatches_askWhichOne_andSignInToNoneOfThem() {
        User zeta = member(UserStatus.ACTIVE);
        User acme = member(UserStatus.ACTIVE);
        User platform = platformAdmin();
        UUID candidateId = UUID.randomUUID();
        passwordFits(zeta);
        passwordFits(acme);
        passwordFits(platform);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(zeta, platform, acme));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD))
                .thenReturn(CandidateCredentialCheck.checked(candidateId, true));
        when(tenantService.findCompanySummaries(anyCollection())).thenReturn(Map.of(
                zeta.getTenantId(), company(zeta, "Zeta Labs"),
                acme.getTenantId(), company(acme, "acme")));
        when(loginSelectionService.issue(anyCollection())).thenReturn("raw-selection-token");
        when(loginSelectionService.ttlSeconds()).thenReturn(120L);
        LoginRequest request = credentials();

        assertThatThrownBy(() -> service.login(null, request))
                .isInstanceOfSatisfying(WorkspaceSelectionRequiredException.class, ex -> {
                    WorkspaceSelectionResponse details = (WorkspaceSelectionResponse) ex.getDetails();
                    assertThat(details.selectionToken()).isEqualTo("raw-selection-token");
                    assertThat(details.expiresInSeconds()).isEqualTo(120L);
                    // Companies by name (ignoring case), then the platform account, then the candidate.
                    assertThat(details.accounts())
                            .extracting(Choice::accountId, Choice::type, Choice::workspaceName)
                            .containsExactly(
                                    tuple(acme.getId(), AccountType.COMPANY, "acme"),
                                    tuple(zeta.getId(), AccountType.COMPANY, "Zeta Labs"),
                                    tuple(platform.getId(), AccountType.PLATFORM, null),
                                    tuple(candidateId, AccountType.CANDIDATE, null));
                });

        verify(loginSelectionService).issue(offered.capture());
        assertThat(offered.getValue()).containsExactlyInAnyOrder(
                acme.getId(), zeta.getId(), platform.getId(), candidateId);
        verify(loginAttemptService, never()).recordSuccess(any());
        verify(loginAttemptService, never()).recordFailure(any());
        verifyNoInteractions(refreshTokenService);
    }

    // ------------------------------------------------------------- no match

    @Test
    void noMatch_countsAFailureAgainstEveryAccountItWasCheckedAgainst() {
        User acme = member(UserStatus.ACTIVE);
        User zeta = member(UserStatus.ACTIVE);
        UUID candidateId = UUID.randomUUID();
        passwordDoesNotFit(acme);
        passwordDoesNotFit(zeta);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(acme, zeta));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD))
                .thenReturn(CandidateCredentialCheck.checked(candidateId, false));
        LoginRequest request = credentials();

        assertThatThrownBy(() -> service.login(null, request)).isInstanceOf(BadCredentialsException.class);

        verify(loginAttemptService).recordFailure(acme.getId());
        verify(loginAttemptService).recordFailure(zeta.getId());
        verify(candidateAuthService).recordFailedSignIn(candidateId);
    }

    @Test
    void lockedAccounts_areNeverChecked_soTheLockCannotBeGuessedAround() {
        User locked = member(UserStatus.ACTIVE);
        lock(locked, Instant.now().plus(Duration.ofMinutes(10)));
        User open = member(UserStatus.ACTIVE);
        passwordDoesNotFit(open);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(locked, open));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD)).thenReturn(CandidateCredentialCheck.NONE);
        LoginRequest request = credentials();

        // Another account is still open, so this is the ordinary 401.
        assertThatThrownBy(() -> service.login(null, request)).isInstanceOf(BadCredentialsException.class);

        verify(passwordEncoder, never()).matches(PASSWORD, locked.getPasswordHash());
        verify(loginAttemptService, never()).recordFailure(locked.getId());
        verify(loginAttemptService).recordFailure(open.getId());
    }

    @Test
    void whenEveryAccountIsLocked_theEarliestUnlockIsReported() {
        Instant soonest = Instant.now().plus(Duration.ofMinutes(3));
        User first = member(UserStatus.ACTIVE);
        lock(first, Instant.now().plus(Duration.ofMinutes(10)));
        User second = member(UserStatus.ACTIVE);
        lock(second, soonest);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(first, second));
        when(candidateAuthService.checkCredentials(EMAIL, PASSWORD)).thenReturn(
                CandidateCredentialCheck.locked(UUID.randomUUID(), Instant.now().plus(Duration.ofMinutes(20))));
        LoginRequest request = credentials();

        assertThatThrownBy(() -> service.login(null, request))
                .isInstanceOfSatisfying(AccountLockedException.class,
                        ex -> assertThat(ex.getLockedUntil()).isEqualTo(soonest));

        verifyNoInteractions(passwordEncoder);
        verify(loginAttemptService, never()).recordFailure(any());
    }

    @Test
    void disabledAccounts_areLeftOutEntirely() {
        User disabled = member(UserStatus.DISABLED);
        when(userRepository.findAllByEmail(EMAIL)).thenReturn(List.of(disabled));
        when(candidateAuthService.authenticate(EMAIL, PASSWORD)).thenReturn(Optional.empty());
        LoginRequest request = credentials();

        assertThatThrownBy(() -> service.login(null, request)).isInstanceOf(BadCredentialsException.class);

        verifyNoInteractions(passwordEncoder, loginAttemptService);
    }

    // ------------------------------------------------------------ the choice

    @Test
    void choosingACompanyAccount_signsInToIt() {
        User acme = member(UserStatus.ACTIVE);
        when(loginSelectionService.redeem("raw", acme.getId())).thenReturn(acme.getId());
        when(userRepository.findById(acme.getId())).thenReturn(Optional.of(acme));
        expectTokens();

        AuthResponse response = service.selectWorkspace(new SelectWorkspaceRequest("raw", acme.getId()));

        assertThat(response.user().tenantId()).isEqualTo(acme.getTenantId());
        verify(loginAttemptService).recordSuccess(acme.getId());
    }

    @Test
    void choosingTheCandidateAccount_completesTheCandidateSignIn() {
        CandidateProfile candidate = candidateProfile();
        when(loginSelectionService.redeem("raw", candidate.id())).thenReturn(candidate.id());
        when(userRepository.findById(candidate.id())).thenReturn(Optional.empty());
        when(candidateAuthService.completeSignIn(candidate.id())).thenReturn(Optional.of(candidate));
        expectTokens();

        AuthResponse response = service.selectWorkspace(new SelectWorkspaceRequest("raw", candidate.id()));

        assertThat(response.user().role()).isEqualTo("CANDIDATE");
    }

    @Test
    void choosingAnAccountLockedSinceTheCheck_isRefused() {
        User acme = member(UserStatus.ACTIVE);
        lock(acme, Instant.now().plus(Duration.ofMinutes(5)));
        when(loginSelectionService.redeem("raw", acme.getId())).thenReturn(acme.getId());
        when(userRepository.findById(acme.getId())).thenReturn(Optional.of(acme));
        SelectWorkspaceRequest request = new SelectWorkspaceRequest("raw", acme.getId());

        assertThatThrownBy(() -> service.selectWorkspace(request)).isInstanceOf(AccountLockedException.class);

        verify(loginAttemptService, never()).recordSuccess(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void choosingAnAccountThatNoLongerExists_isTheGeneric401() {
        UUID gone = UUID.randomUUID();
        when(loginSelectionService.redeem("raw", gone)).thenReturn(gone);
        when(userRepository.findById(gone)).thenReturn(Optional.empty());
        when(candidateAuthService.completeSignIn(gone)).thenReturn(Optional.empty());
        SelectWorkspaceRequest request = new SelectWorkspaceRequest("raw", gone);

        assertThatThrownBy(() -> service.selectWorkspace(request)).isInstanceOf(BadCredentialsException.class);
    }

    // ---------------------------------------------------------------- helpers

    private static LoginRequest credentials() {
        return new LoginRequest("Ada@Acme.io", PASSWORD);
    }

    /** A member of a fresh workspace, with a password hash of its own. */
    private static User member(UserStatus status) {
        UUID tenantId = UUID.randomUUID();
        TenantRole role = TenantRole.fromTemplate(tenantId, SystemRole.RECRUITER);
        ReflectionTestUtils.setField(role, "id", UUID.randomUUID());
        return withId(new User(tenantId, role, null, EMAIL, "hash-" + UUID.randomUUID(),
                "Ada", "Lovelace", status));
    }

    private static User platformAdmin() {
        return withId(new User(null, null, null, EMAIL, "hash-" + UUID.randomUUID(),
                "Ada", "Lovelace", UserStatus.ACTIVE));
    }

    private static User withId(User user) {
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private static void lock(User user, Instant until) {
        ReflectionTestUtils.setField(user, "lockedUntil", until);
    }

    private void passwordFits(User user) {
        when(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).thenReturn(true);
    }

    private void passwordDoesNotFit(User user) {
        when(passwordEncoder.matches(PASSWORD, user.getPasswordHash())).thenReturn(false);
    }

    private void expectTokens() {
        when(jwtTokenProvider.getAccessTokenTtl()).thenReturn(Duration.ofMinutes(15));
    }

    private static CompanySummaryResponse company(User user, String name) {
        return new CompanySummaryResponse(user.getTenantId(), name, "sub-" + name.length(), null, null);
    }

    private static CandidateProfile candidateProfile() {
        return new CandidateProfile(UUID.randomUUID(), EMAIL, "Ada", "Lovelace", "ACTIVE", Instant.now());
    }
}
