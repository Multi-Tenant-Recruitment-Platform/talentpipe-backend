package com.talentpipe.job.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobVacancyMemberValidatorTest {

    @Mock
    private UserDirectoryService userDirectoryService;

    private JobVacancyMemberValidator validator;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        validator = new JobVacancyMemberValidator(userDirectoryService);
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

        when(userDirectoryService.isActiveMember(tenantId, recruiterId)).thenReturn(true);
        when(userDirectoryService.isActiveMember(tenantId, managerId)).thenReturn(true);

        assertThatCode(() -> validator.validateMembers(tenantId, recruiterId, managerId))
                .doesNotThrowAnyException();
    }

    @Test
    void validateMembers_recruiterNotActive_throws422() {
        UUID recruiterId = UUID.randomUUID();
        when(userDirectoryService.isActiveMember(tenantId, recruiterId)).thenReturn(false);

        assertThatThrownBy(() -> validator.validateMembers(tenantId, recruiterId, null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The assigned recruiter must be an active member of your workspace.");
    }

    @Test
    void validateMembers_managerNotActive_throws422() {
        UUID managerId = UUID.randomUUID();
        when(userDirectoryService.isActiveMember(tenantId, managerId)).thenReturn(false);

        assertThatThrownBy(() -> validator.validateMembers(tenantId, null, managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The hiring manager must be an active member of your workspace.");
    }

    @Test
    void validateMembers_managerInAnotherTenant_throws422() {
        UUID managerId = UUID.randomUUID();
        when(userDirectoryService.isActiveMember(tenantId, managerId)).thenReturn(false);

        assertThatThrownBy(() -> validator.validateMembers(tenantId, null, managerId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The hiring manager must be an active member of your workspace.");
    }

    @Test
    void validateMembersForUpdate_unchangedInactiveAssignee_succeeds() {
        UUID departedRecruiterId = UUID.randomUUID();
        // Unchanged assignee: not validated against directory
        assertThatCode(() -> validator.validateMembersForUpdate(
                tenantId,
                departedRecruiterId, departedRecruiterId,
                null, null
        )).doesNotThrowAnyException();
    }
}
