package com.talentpipe.job.mapper;

import com.talentpipe.job.dto.JobDetailResponse;
import com.talentpipe.job.dto.JobSummaryResponse;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.tenant.dto.CompanySummaryResponse;
import org.springframework.stereotype.Component;

/**
 * Entity → DTO mapping for the job module. Entities never leave the module raw.
 *
 * <p>There are two audiences, and the mapping is where they are kept apart:
 * {@link #toResponse} is the recruiter's full view, while {@link #toSummary}
 * and {@link #toDetail} build the public advert and copy across only what a
 * company chose to advertise. A field added to the entity is therefore private
 * by default — it reaches candidates only if someone adds it here on purpose.</p>
 */
@Component
public class JobVacancyMapper {

    /** The recruiter dashboard's view of a vacancy. */
    public JobVacancyResponse toResponse(JobVacancy vacancy) {
        VacancyContent content = vacancy.content();
        return new JobVacancyResponse(
                vacancy.getId(),
                vacancy.getVersion(),
                vacancy.getStatus(),

                content.title(),
                content.department(),
                content.openings(),
                content.employmentType(),
                content.workplaceType(),
                content.location(),
                content.applicationDeadline(),

                content.jobSummary(),
                content.jobDescription(),
                content.keyResponsibilities(),

                content.requiredSkills(),
                content.preferredSkills(),
                content.minimumExperienceYears(),
                content.education(),
                content.certifications(),
                content.languageRequirements(),
                content.otherRequirements(),

                content.salaryMin(),
                content.salaryMax(),
                content.currency(),
                content.payPeriod(),
                content.benefits(),

                content.workingDays(),
                content.workingHours(),
                content.shiftType(),
                content.expectedHoursPerWeek(),

                content.assignedRecruiterId(),
                content.hiringManagerId(),
                content.recruitmentPipelineId(),
                content.screeningQuestions(),

                vacancy.getCreatedAt(),
                vacancy.getUpdatedAt(),
                vacancy.getPublishedAt(),
                vacancy.getClosedAt(),
                vacancy.getArchivedAt(),
                // TODO(sprint3): real count once the applications module is
                // merged. Null, not zero — see JobVacancyResponse.
                null);
    }

    /**
     * A card on the public job board.
     *
     * @param company               the owning company, or {@code null} if it
     *                              could not be resolved — the card then shows
     *                              no company name rather than failing the page
     * @param acceptingApplications whether a candidate may apply right now
     */
    public JobSummaryResponse toSummary(JobVacancy vacancy, CompanySummaryResponse company,
                                        boolean acceptingApplications) {
        VacancyContent content = vacancy.content();
        String companyName = company == null ? "" : company.name();
        return new JobSummaryResponse(
                vacancy.getId(),
                JobSlug.of(content.title(), companyName, vacancy.getId()),
                content.title(),
                companyName,
                company == null ? null : company.logoUrl(),
                content.department(),
                content.openings(),
                content.employmentType(),
                content.workplaceType(),
                content.location(),
                content.applicationDeadline(),
                content.jobSummary(),
                content.requiredSkills(),
                content.minimumExperienceYears(),
                content.salaryMin(),
                content.salaryMax(),
                content.currency(),
                content.payPeriod(),
                vacancy.getPublishedAt(),
                acceptingApplications);
    }

    /** The full public advert: the card plus the rest of what the company wrote. */
    public JobDetailResponse toDetail(JobVacancy vacancy, CompanySummaryResponse company,
                                      boolean acceptingApplications) {
        VacancyContent content = vacancy.content();
        return new JobDetailResponse(
                toSummary(vacancy, company, acceptingApplications),
                content.jobDescription(),
                content.keyResponsibilities(),
                content.preferredSkills(),
                content.education(),
                content.certifications(),
                content.languageRequirements(),
                content.otherRequirements(),
                content.benefits(),
                content.workingDays(),
                content.workingHours(),
                content.shiftType(),
                content.expectedHoursPerWeek());
    }
}
