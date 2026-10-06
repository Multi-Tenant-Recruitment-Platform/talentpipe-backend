package com.talentpipe.job.validator;

import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.BusinessRuleException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates that assigned recruiters and hiring managers are active members of the tenant.
 */
@Component
public class JobVacancyMemberValidator {

    private final UserRepository userRepository;

    public JobVacancyMemberValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void validateMembers(UUID tenantId, UUID assignedRecruiterId, UUID hiringManagerId) {
        if (assignedRecruiterId != null) {
            validateUserIsActiveMember(
                    tenantId,
                    assignedRecruiterId,
                    "The assigned recruiter must be an active member of your organization."
            );
        }
        if (hiringManagerId != null) {
            validateUserIsActiveMember(
                    tenantId,
                    hiringManagerId,
                    "The hiring manager must be an active member of your organization."
            );
        }
    }

    private void validateUserIsActiveMember(UUID tenantId, UUID userId, String errorMessage) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.getTenantId() == null || !user.getTenantId().equals(tenantId)
                || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessRuleException(errorMessage);
        }
    }
}
