package com.talentpipe.candidate.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.candidate.dto.CandidateProfile;
import com.talentpipe.candidate.dto.CandidateRegisterRequest;
import com.talentpipe.candidate.entity.Candidate;
import com.talentpipe.candidate.repository.CandidateRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class CandidateServiceTest {

    @Mock
    private CandidateRepository candidateRepository;

    @Mock
    private CandidateVerificationService verificationService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private CandidateService candidateService;

    @Test
    void register_success_savesCandidateAndIssuesVerification() {
        CandidateRegisterRequest request = new CandidateRegisterRequest(
                "  Kasun Perera  ",
                "200012345678",
                "  Colombo, Sri Lanka  ",
                "  +94 77 123 4567  ",
                "  Kasun@Example.COM  ",
                "Password123!");

        when(candidateRepository.existsByEmail("kasun@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Password123!")).thenReturn("encoded-secret");
        when(candidateRepository.save(any(Candidate.class))).thenAnswer(inv -> {
            Candidate c = inv.getArgument(0);
            return c;
        });

        CandidateProfile profile = candidateService.register(request);

        assertThat(profile.email()).isEqualTo("kasun@example.com");
        assertThat(profile.firstName()).isEqualTo("Kasun");
        assertThat(profile.lastName()).isEqualTo("Perera");
        assertThat(profile.status()).isEqualTo(UserStatus.PENDING_VERIFICATION.name());
        verify(verificationService).issueAndSend(any(Candidate.class));
    }

    @Test
    void register_duplicateEmail_throwsDuplicateResourceException() {
        CandidateRegisterRequest request = new CandidateRegisterRequest(
                "Kasun Perera",
                "200012345678",
                "Colombo",
                "+94 77 123 4567",
                "kasun@example.com",
                "Password123!");

        when(candidateRepository.existsByEmail("kasun@example.com")).thenReturn(true);

        assertThatThrownBy(() -> candidateService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already registered");
    }
}
