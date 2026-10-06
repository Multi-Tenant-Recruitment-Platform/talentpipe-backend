package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.entity.Role;
import com.talentpipe.auth.entity.RoleName;
import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class JobVacancyMemberValidatorTest {

    @Mock
    private UserRepository userRepository;

    private JobVacancyMemberValidator validator;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        validator = new JobVacancyMemberValidator(userRepository);
        tenantId = UUID.randomUUID();
    }

    @Test
    void validateMembers_bothNull_succeeds() {
        assertThatCode(() -> validator.validateMembers(tenantId, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void validateMembers_validActiveRecruiterAndManager_succeeds() {
        UUID recruiterId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        User recruiter = createUser(tenantId, UserStatus.ACTIVE);
        User manager = createUser(tenantId, UserStatus.ACTIVE);

        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(recruiter));
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatCode(() -> validator.validateMembers(tenantId, recruiterId, managerId))
                .doesNotThrowAnyException();
    }

    @Test
    void validateMembers_recruiterNotFound_throws422() {
        UUID recruiterId = UUID.randomUUID();
        when(userRepository.findById(recruiterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateMembers(tenantId, recruiterId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your organization.");
    }

    @Test
    void validateMembers_recruiterDifferentTenant_throws422() {
        UUID recruiterId = UUID.randomUUID();
        User recruiter = createUser(UUID.randomUUID(), UserStatus.ACTIVE);
        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(recruiter));

        assertThatThrownBy(() -> validator.validateMembers(tenantId, recruiterId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your organization.");
    }

    @Test
    void validateMembers_recruiterNotActive_throws422() {
        UUID recruiterId = UUID.randomUUID();
        User recruiter = createUser(tenantId, UserStatus.DISABLED);
        when(userRepository.findById(recruiterId)).thenReturn(Optional.of(recruiter));

        assertThatThrownBy(() -> validator.validateMembers(tenantId, recruiterId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your organization.");
    }

    @Test
    void validateMembers_hiringManagerNotFound_throws422() {
        UUID managerId = UUID.randomUUID();
        when(userRepository.findById(managerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateMembers(tenantId, null, managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The hiring manager must be an active member of your organization.");
    }

    @Test
    void validateMembers_hiringManagerDifferentTenant_throws422() {
        UUID managerId = UUID.randomUUID();
        User manager = createUser(UUID.randomUUID(), UserStatus.ACTIVE);
        when(userRepository.findById(managerId)).thenReturn(Optional.of(manager));

        assertThatThrownBy(() -> validator.validateMembers(tenantId, null, managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The hiring manager must be an active member of your organization.");
    }

    private User createUser(UUID userTenantId, UserStatus status) {
        User user = org.mockito.Mockito.mock(User.class);
        org.mockito.Mockito.lenient().when(user.getTenantId()).thenReturn(userTenantId);
        org.mockito.Mockito.lenient().when(user.getStatus()).thenReturn(status);
        return user;
    }
}
