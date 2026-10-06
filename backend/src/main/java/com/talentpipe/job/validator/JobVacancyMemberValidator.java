package com.talentpipe.job.validator;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates that assigned recruiters and hiring managers are active members of the tenant.
 */
@Component
public class JobVacancyMemberValidator {

    private final UserDirectoryService userDirectoryService;

    public JobVacancyMemberValidator(UserDirectoryService userDirectoryService) {
        this.userDirectoryService = userDirectoryService;
    }

    public void validateMembers(UUID tenantId, UUID assignedRecruiterId, UUID hiringManagerId) {
        if (assignedRecruiterId != null && !userDirectoryService.isActiveMember(tenantId, assignedRecruiterId)) {
            throw new BusinessRuleException("The assigned recruiter must be an active member of your organization.");
        }
        if (hiringManagerId != null && !userDirectoryService.isActiveMember(tenantId, hiringManagerId)) {
            throw new BusinessRuleException("The hiring manager must be an active member of your organization.");
        }
    }
}
