package com.talentpipe.job.service;

import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.entity.VacancyContent;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Business rules that hold for a vacancy's content in <em>every</em> state,
 * drafts included — as opposed to {@code VacancyPublishRules}, which only
 * decide whether an advert is complete enough to be public.
 *
 * <p>Each rule spans more than one field, or reaches outside the request, so
 * bean validation on the DTO cannot express it. A violation is a 422 with a
 * sentence the frontend shows to the recruiter as written.</p>
 */
@Component
public class VacancyContentRules {

    /**
     * The stage templates a vacancy can follow. A fixed set for now: the
     * pipeline module does not exist yet, and these are the ids the vacancy
     * form offers.
     *
     * <p>TODO(sprint3): replace with a lookup against the pipeline module once
     * tenants can define their own pipelines.</p>
     */
    static final Set<String> PIPELINE_TEMPLATES = Set.of("STANDARD", "TECHNICAL", "EXECUTIVE");

    private final UserDirectoryService userDirectory;

    public VacancyContentRules(UserDirectoryService userDirectory) {
        this.userDirectory = userDirectory;
    }

    /**
     * @param tenantId the workspace the vacancy belongs to
     * @param content  the normalized content about to be stored
     * @param previous what the vacancy holds now, or {@code null} when it is
     *                 being created
     * @throws BusinessRuleException (422) on the first rule that fails
     */
    public void check(UUID tenantId, VacancyContent content, VacancyContent previous) {
        checkSalary(content);
        checkPipeline(content);
        checkAssignee(tenantId, content.assignedRecruiterId(),
                previous == null ? null : previous.assignedRecruiterId(), "The assigned recruiter");
        checkAssignee(tenantId, content.hiringManagerId(),
                previous == null ? null : previous.hiringManagerId(), "The hiring manager");
    }

    /**
     * A salary figure with no unit is not a salary: a candidate reading
     * "150,000" has to guess both the currency and whether it is a month or a
     * year. So a range needs both, and its ends must be the right way round.
     */
    private void checkSalary(VacancyContent content) {
        boolean hasSalary = content.salaryMin() != null || content.salaryMax() != null;
        if (!hasSalary) {
            return;
        }
        if (content.salaryMin() != null && content.salaryMax() != null
                && content.salaryMax().compareTo(content.salaryMin()) < 0) {
            throw new BusinessRuleException("The maximum salary must be at least the minimum.");
        }
        if (content.currency() == null) {
            throw new BusinessRuleException("Choose a currency for the salary range you entered.");
        }
        if (content.payPeriod() == null) {
            throw new BusinessRuleException("Say whether the salary range is hourly, monthly or yearly.");
        }
    }

    private void checkPipeline(VacancyContent content) {
        String pipelineId = content.recruitmentPipelineId();
        if (pipelineId != null && !PIPELINE_TEMPLATES.contains(pipelineId)) {
            throw new BusinessRuleException("Choose one of the available recruitment pipelines.");
        }
    }

    /**
     * A person can only be <em>newly</em> assigned if they are an active
     * member of this workspace. The message is the same whether the id
     * belongs to another tenant or to nobody, so it reveals nothing about
     * users elsewhere.
     *
     * <p>An assignment that is not changing is left alone, even if that person
     * has since been deactivated. Otherwise a vacancy whose recruiter left the
     * company could not be edited at all until someone noticed and cleared a
     * field unrelated to the edit they were trying to make.</p>
     */
    private void checkAssignee(UUID tenantId, UUID userId, UUID previousUserId, String role) {
        if (userId == null || Objects.equals(userId, previousUserId)) {
            return;
        }
        if (!userDirectory.isActiveMember(tenantId, userId)) {
            throw new BusinessRuleException(role + " must be an active member of your workspace.");
        }
    }
}
