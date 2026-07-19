package com.talentpipe.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.dto.RegisterRequest;
import com.talentpipe.auth.dto.RegisterResponse;
import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.mapper.UserMapper;
import com.talentpipe.auth.repository.RoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.security.JwtTokenProvider;
import com.talentpipe.tenant.dto.TenantResponse;
import com.talentpipe.tenant.service.TenantService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Unit tests for the registration use-case (PB-001). */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private TenantService tenantService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private EmailVerificationService emailVerificationService;
    @Mock
    private com.talentpipe.candidate.repository.CandidateRepository candidateRepository;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService service(UserMapper mapper) {
        return new AuthService(userRepository, roleRepository, tenantService,
                refreshTokenService, emailVerificationService, candidateRepository, jwtTokenProvider, passwordEncoder, mapper);
    }

    private static RegisterRequest acmeRequest() {
        return new RegisterRequest("Acme Inc", "acme",
                new RegisterRequest.AdminUser("Ada", "Lovelace", "Ada@Acme.io", "s3cret-password"));
    }

    @Test
    void register_createsTenantAndCompanyAdmin_withHashedPassword() {
        UUID tenantId = UUID.randomUUID();
        when(tenantService.createTenant("Acme Inc", "acme")).thenReturn(new TenantResponse(
                tenantId, "Acme Inc", "acme", null, "STANDARD", "ACTIVE", Instant.now()));

        Role adminRole = mock(Role.class);
        when(adminRole.getName()).thenReturn(RoleName.COMPANY_ADMIN);
        when(roleRepository.findByName(RoleName.COMPANY_ADMIN)).thenReturn(Optional.of(adminRole));

        when(passwordEncoder.encode("s3cret-password")).thenReturn("$2a$12$mocked-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RegisterResponse response = service(new UserMapper()).register(acmeRequest());

        // The persisted user: correct tenant, role, normalized email, HASH not raw password.
        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertThat(savedUser.getValue().getTenantId()).isEqualTo(tenantId);
        assertThat(savedUser.getValue().getEmail()).isEqualTo("ada@acme.io");
        assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("$2a$12$mocked-hash");
        // Account starts directly as ACTIVE to bypass verification blocker during testing.
        assertThat(savedUser.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

        // The response DTO: tenant + admin, no credential material anywhere.
        assertThat(response.tenant().subdomain()).isEqualTo("acme");
        assertThat(response.admin().role()).isEqualTo("COMPANY_ADMIN");
        assertThat(response.admin().tenantName()).isEqualTo("Acme Inc");
        assertThat(response.admin()).hasNoNullFieldsOrPropertiesExcept("id", "createdAt");
    }

    @Test
    void register_duplicateSubdomain_propagatesConflictAndCreatesNoUser() {
        when(tenantService.createTenant("Acme Inc", "acme"))
                .thenThrow(new DuplicateResourceException("Subdomain 'acme' is already taken"));

        assertThatThrownBy(() -> service(new UserMapper()).register(acmeRequest()))
                .isInstanceOf(DuplicateResourceException.class);

        verify(userRepository, never()).save(any());
    }
}
