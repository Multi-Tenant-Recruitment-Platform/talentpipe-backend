package com.talentpipe.job.validator;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates that assigned recruiters and hiring managers are active members of the workspace.
 */
@Component
public class JobVacancyMemberValidator {

    private final UserDirectoryService userDirectoryService;

    public JobVacancyMemberValidator(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    public void validateMembers(UUID tenantId, UUID assignedRecruiterId, UUID hiringManagerId) {
        validateMember(tenantId, assignedRecruiterId, null, "The assigned recruiter");
        validateMember(tenantId, hiringManagerId, null, "The hiring manager");
    }

    public void validateMembersForUpdate(
            UUID tenantId,
            UUID assignedRecruiterId, UUID previousRecruiterId,
            UUID hiringManagerId, UUID previousManagerId
    ) {
        validateMember(tenantId, assignedRecruiterId, previousRecruiterId, "The assigned recruiter");
        validateMember(tenantId, hiringManagerId, previousManagerId, "The hiring manager");
    }

    private void validateMember(UUID tenantId, UUID userId, UUID previousUserId, String role) {
        if (userId == null || Objects.equals(userId, previousUserId)) {
            return;
        }
        if (!userDirectoryService.isActiveMember(tenantId, userId)) {
            throw new BusinessRuleException(role + " must be an active member of your workspace.");
        }
    }
}
