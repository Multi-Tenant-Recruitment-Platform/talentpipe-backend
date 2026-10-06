package com.talentpipe.job.mapper;

import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.dto.JobVacancyResponse;
import com.talentpipe.job.dto.JobVacancyUpdateRequest;
import com.talentpipe.job.entity.JobVacancy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Maps between JobVacancy entities and request/response DTOs.
 */
@Component
public class JobVacancyMapper {

    public JobVacancy toEntity(UUID tenantId, JobVacancyRequest request) {
        JobVacancy vacancy = new JobVacancy();
        vacancy.setTenantId(tenantId);
        vacancy.setStatus(request.status());
        updateContentFields(vacancy,
                request.title(),
                request.department(),
                request.openings(),
                request.employmentType(),
                request.workplaceType(),
                request.location(),
                request.applicationDeadline(),
                request.jobSummary(),
                request.jobDescription(),
                request.keyResponsibilities(),
                request.requiredSkills(),
                request.preferredSkills(),
                request.minimumExperienceYears(),
                request.education(),
                request.certifications(),
                request.languageRequirements(),
                request.otherRequirements(),
                request.salaryMin(),
                request.salaryMax(),
                request.currency(),
                request.payPeriod(),
                request.benefits(),
                request.workingDays(),
                request.workingHours(),
                request.shiftType(),
                request.expectedHoursPerWeek(),
                request.assignedRecruiterId(),
                request.hiringManagerId(),
                request.recruitmentPipelineId(),
                request.screeningQuestions()
        );
        return vacancy;
    }

    public void updateEntity(JobVacancy vacancy, JobVacancyUpdateRequest request) {
        updateContentFields(vacancy,
                request.title(),
                request.department(),
                request.openings(),
                request.employmentType(),
                request.workplaceType(),
                request.location(),
                request.applicationDeadline(),
                request.jobSummary(),
                request.jobDescription(),
                request.keyResponsibilities(),
                request.requiredSkills(),
                request.preferredSkills(),
                request.minimumExperienceYears(),
                request.education(),
                request.certifications(),
                request.languageRequirements(),
                request.otherRequirements(),
                request.salaryMin(),
                request.salaryMax(),
                request.currency(),
                request.payPeriod(),
                request.benefits(),
                request.workingDays(),
                request.workingHours(),
                request.shiftType(),
                request.expectedHoursPerWeek(),
                request.assignedRecruiterId(),
                request.hiringManagerId(),
                request.recruitmentPipelineId(),
                request.screeningQuestions()
        );
    }

    private void updateContentFields(
            JobVacancy vacancy,
            String title,
            String department,
            Integer openings,
            com.talentpipe.job.enums.EmploymentType employmentType,
            com.talentpipe.job.enums.WorkplaceType workplaceType,
            String location,
            java.time.LocalDate applicationDeadline,
            String jobSummary,
            String jobDescription,
            List<String> keyResponsibilities,
            List<String> requiredSkills,
            List<String> preferredSkills,
            Integer minimumExperienceYears,
            String education,
            List<String> certifications,
            List<String> languageRequirements,
            String otherRequirements,
            java.math.BigDecimal salaryMin,
            java.math.BigDecimal salaryMax,
            String currency,
            com.talentpipe.job.enums.PayPeriod payPeriod,
            List<String> benefits,
            List<com.talentpipe.job.enums.WeekDay> workingDays,
            String workingHours,
            com.talentpipe.job.enums.ShiftType shiftType,
            Double expectedHoursPerWeek,
            UUID assignedRecruiterId,
            UUID hiringManagerId,
            String recruitmentPipelineId,
            List<String> screeningQuestions
    ) {
        vacancy.setTitle(title);
        vacancy.setDepartment(department);
        vacancy.setOpenings(openings != null ? openings : 1);
        vacancy.setEmploymentType(employmentType);
        vacancy.setWorkplaceType(workplaceType);
        vacancy.setLocation(location);
        vacancy.setApplicationDeadline(applicationDeadline);
        vacancy.setJobSummary(jobSummary);
        vacancy.setJobDescription(jobDescription);
        vacancy.setKeyResponsibilities(defensiveListCopy(keyResponsibilities));
        vacancy.setRequiredSkills(defensiveListCopy(requiredSkills));
        vacancy.setPreferredSkills(defensiveListCopy(preferredSkills));
        vacancy.setMinimumExperienceYears(minimumExperienceYears);
        vacancy.setEducation(education);
        vacancy.setCertifications(defensiveListCopy(certifications));
        vacancy.setLanguageRequirements(defensiveListCopy(languageRequirements));
        vacancy.setOtherRequirements(otherRequirements);
        vacancy.setSalaryMin(salaryMin);
        vacancy.setSalaryMax(salaryMax);
        vacancy.setCurrency(currency);
        vacancy.setPayPeriod(payPeriod);
        vacancy.setBenefits(defensiveListCopy(benefits));
        vacancy.setWorkingDays(workingDays != null ? new ArrayList<>(workingDays) : new ArrayList<>());
        vacancy.setWorkingHours(workingHours);
        vacancy.setShiftType(shiftType);
        vacancy.setExpectedHoursPerWeek(expectedHoursPerWeek);
        vacancy.setAssignedRecruiterId(assignedRecruiterId);
        vacancy.setHiringManagerId(hiringManagerId);
        vacancy.setRecruitmentPipelineId(recruitmentPipelineId);
        vacancy.setScreeningQuestions(defensiveListCopy(screeningQuestions));
    }

    public JobVacancyResponse toResponse(JobVacancy vacancy) {
        return new JobVacancyResponse(
                vacancy.getId(),
                vacancy.getVersion(),
                vacancy.getStatus(),
                vacancy.getTitle(),
                vacancy.getDepartment(),
                vacancy.getOpenings(),
                vacancy.getEmploymentType(),
                vacancy.getWorkplaceType(),
                vacancy.getLocation(),
                vacancy.getApplicationDeadline(),
                vacancy.getJobSummary(),
                vacancy.getJobDescription(),
                vacancy.getKeyResponsibilities(),
                vacancy.getRequiredSkills(),
                vacancy.getPreferredSkills(),
                vacancy.getMinimumExperienceYears(),
                vacancy.getEducation(),
                vacancy.getCertifications(),
                vacancy.getLanguageRequirements(),
                vacancy.getOtherRequirements(),
                vacancy.getSalaryMin(),
                vacancy.getSalaryMax(),
                vacancy.getCurrency(),
                vacancy.getPayPeriod(),
                vacancy.getBenefits(),
                vacancy.getWorkingDays(),
                vacancy.getWorkingHours(),
                vacancy.getShiftType(),
                vacancy.getExpectedHoursPerWeek(),
                vacancy.getAssignedRecruiterId(),
                vacancy.getHiringManagerId(),
                vacancy.getRecruitmentPipelineId(),
                vacancy.getScreeningQuestions(),
                vacancy.getCreatedAt(),
                vacancy.getUpdatedAt(),
                vacancy.getPublishedAt(),
                vacancy.getClosedAt(),
                vacancy.getArchivedAt(),
                null // applicantCount remains null per API contract
        );
    }

    private <T> List<T> defensiveListCopy(List<T> source) {
        return source != null ? new ArrayList<>(source) : new ArrayList<>();
    }
}
