package com.talentpipe.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.CandidateCredentialCheck;
import com.talentpipe.candidate.dto.CandidateCredentialCheck.Outcome;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.AccountLockedException;
import com.talentpipe.common.exception.AccountNotVerifiedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for candidate sign-in, in particular the side-effect-free check
 * and the completion step used when the same credentials also fit a company
 * account.
 */
@ExtendWith(MockitoExtension.class)
class CandidateAuthServiceTest {

    private static final String EMAIL = "nimal@example.lk";
    private static final String PASSWORD = "s3cret-password";
    private static final String HASH = "$2a$12$hash";

    @Mock
    private CandidateRepository candidateRepository;
    @Mock
    private CandidateVerificationService verificationService;
    @Mock
    private CandidateLoginAttemptService loginAttempts;
    @Mock
    private PasswordEncoder passwordEncoder;

    private CandidateAuthService service;

    @BeforeEach
    void setUp() {
        service = new CandidateAuthService(candidateRepository, verificationService, loginAttempts, passwordEncoder);
    }

    // ------------------------------------------------------- checkCredentials

    @Test
    void check_reportsAMatch_withoutRecordingAnything() {
        Candidate candidate = stored(UserStatus.ACTIVE);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        CandidateCredentialCheck check = service.checkCredentials(" Nimal@Example.lk ", PASSWORD);

        assertThat(check.matched()).isTrue();
        assertThat(check.candidateId()).isEqualTo(candidate.getId());
        verifyNoInteractions(loginAttempts);
    }

    @Test
    void check_reportsAWrongPassword_withoutCountingIt() {
        stored(UserStatus.ACTIVE);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        CandidateCredentialCheck check = service.checkCredentials(EMAIL, PASSWORD);

        assertThat(check.outcome()).isEqualTo(Outcome.WRONG_PASSWORD);
        verifyNoInteractions(loginAttempts);
    }

    @Test
    void check_reportsALock_withoutLookingAtThePassword() {
        Candidate candidate = stored(UserStatus.ACTIVE);
        Instant until = Instant.now().plus(Duration.ofMinutes(5));
        ReflectionTestUtils.setField(candidate, "lockedUntil", until);

        CandidateCredentialCheck check = service.checkCredentials(EMAIL, PASSWORD);

        assertThat(check).isEqualTo(CandidateCredentialCheck.locked(candidate.getId(), until));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void check_treatsADisabledAccount_asNoAccount() {
        stored(UserStatus.DISABLED);

        assertThat(service.checkCredentials(EMAIL, PASSWORD)).isEqualTo(CandidateCredentialCheck.NONE);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void check_reportsNoAccount_forAnUnknownEmail() {
        when(candidateRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.checkCredentials(EMAIL, PASSWORD).outcome()).isEqualTo(Outcome.NO_ACCOUNT);
    }

    @Test
    void recordFailedSignIn_countsTowardsTheLock() {
        UUID candidateId = UUID.randomUUID();

        service.recordFailedSignIn(candidateId);

        verify(loginAttempts).recordFailure(candidateId);
    }

    // --------------------------------------------------------- completeSignIn

    @Test
    void complete_signsInAnActiveCandidate_andClearsTheirFailures() {
        Candidate candidate = storedById(UserStatus.ACTIVE);

        Optional<CandidateProfile> profile = service.completeSignIn(candidate.getId());

        assertThat(profile).map(CandidateProfile::email).contains(EMAIL);
        verify(loginAttempts).recordSuccess(candidate.getId());
    }

    @Test
    void complete_refusesALockedCandidate() {
        Candidate candidate = storedById(UserStatus.ACTIVE);
        ReflectionTestUtils.setField(candidate, "lockedUntil", Instant.now().plus(Duration.ofMinutes(5)));
        UUID candidateId = candidate.getId();

        assertThatThrownBy(() -> service.completeSignIn(candidateId)).isInstanceOf(AccountLockedException.class);
        verify(loginAttempts, never()).recordSuccess(any());
    }

    @Test
    void complete_refusesAnUnverifiedCandidate_withTheReason() {
        UUID candidateId = storedById(UserStatus.PENDING_VERIFICATION).getId();

        assertThatThrownBy(() -> service.completeSignIn(candidateId))
                .isInstanceOf(AccountNotVerifiedException.class);
    }

    @Test
    void complete_givesNothingForADisabledOrMissingCandidate() {
        UUID disabled = storedById(UserStatus.DISABLED).getId();
        UUID missing = UUID.randomUUID();
        when(candidateRepository.findById(missing)).thenReturn(Optional.empty());

        assertThat(service.completeSignIn(disabled)).isEmpty();
        assertThat(service.completeSignIn(missing)).isEmpty();
        verify(loginAttempts, never()).recordSuccess(any());
    }

    // ----------------------------------------------------------- authenticate

    @Test
    void authenticate_countsAWrongPassword() {
        Candidate candidate = stored(UserStatus.ACTIVE);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(false);

        assertThat(service.authenticate(EMAIL, PASSWORD)).isEmpty();
        verify(loginAttempts).recordFailure(candidate.getId());
    }

    @Test
    void authenticate_signsInWithTheRightPassword() {
        Candidate candidate = stored(UserStatus.ACTIVE);
        when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);

        assertThat(service.authenticate(EMAIL, PASSWORD)).map(CandidateProfile::id).contains(candidate.getId());
        verify(loginAttempts).recordSuccess(candidate.getId());
    }

    // ---------------------------------------------------------------- helpers

    private Candidate stored(UserStatus status) {
        Candidate candidate = candidate(status);
        when(candidateRepository.findByEmail(EMAIL)).thenReturn(Optional.of(candidate));
        return candidate;
    }

    private Candidate storedById(UserStatus status) {
        Candidate candidate = candidate(status);
        when(candidateRepository.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        return candidate;
    }

    private static Candidate candidate(UserStatus status) {
        Candidate candidate = new Candidate(EMAIL, HASH, "Nimal Perera", "941234567V",
                "123 Galle Road, Colombo", "+94 77 123 4567", status);
        ReflectionTestUtils.setField(candidate, "id", UUID.randomUUID());
        return candidate;
    }
}
