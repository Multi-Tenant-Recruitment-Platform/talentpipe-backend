package com.talentpipe.job.validator;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.VacancyStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Validates structural shape and constraints on vacancy request payloads.
 *
 * <p>Emits user-friendly 400 messages (via {@link InvalidRequestException}) for
 * structural or range violations, and 422 for unrecognized pipeline templates.</p>
 */
@Component
public class JobVacancyShapeValidator {

    private static final int MAX_TITLE_LENGTH = 120;
    private static final int MAX_LOCATION_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 300;
    private static final int MAX_DESCRIPTION_LENGTH = 5000;
    private static final int MAX_OTHER_REQUIREMENTS_LENGTH = 1000;
    private static final int MAX_WORKING_HOURS_LENGTH = 60;
    private static final int MAX_SCREENING_QUESTIONS = 10;
    private static final int MAX_EXPERIENCE_YEARS = 50;
    private static final BigDecimal MAX_HOURS_PER_WEEK = BigDecimal.valueOf(168);

    private static final Set<String> VALID_PIPELINES = Set.of("STANDARD", "TECHNICAL", "EXECUTIVE");

    public void validateCreationShape(JobVacancyRequest request) {
        validateStatusOnCreate(request.status());
        validateContentShape(
                request.title(),
                request.openings(),
                request.location(),
                request.jobSummary(),
                request.jobDescription(),
                request.otherRequirements(),
                request.minimumExperienceYears(),
                request.workingHours(),
                request.expectedHoursPerWeek(),
                request.salaryMin(),
                request.salaryMax(),
                request.currency(),
                request.payPeriod() != null,
                request.screeningQuestions(),
                request.recruitmentPipelineId()
        );
    }

    public void validateUpdateShape(JobVacancyRequest request) {
        validateContentShape(
                request.title(),
                request.openings(),
                request.location(),
                request.jobSummary(),
                request.jobDescription(),
                request.otherRequirements(),
                request.minimumExperienceYears(),
                request.workingHours(),
                request.expectedHoursPerWeek(),
                request.salaryMin(),
                request.salaryMax(),
                request.currency(),
                request.payPeriod() != null,
                request.screeningQuestions(),
                request.recruitmentPipelineId()
        );
    }

    private void validateStatusOnCreate(VacancyStatus status) {
        if (status == null || (status != VacancyStatus.DRAFT && status != VacancyStatus.PUBLISHED)) {
            throw new InvalidRequestException("A new vacancy can only be saved as a draft or published.");
        }
    }

    private void validateContentShape(
            String title,
            Integer openings,
            String location,
            String jobSummary,
            String jobDescription,
            String otherRequirements,
            Integer minimumExperienceYears,
            String workingHours,
            BigDecimal expectedHoursPerWeek,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String currency,
            boolean hasPayPeriod,
            List<String> screeningQuestions,
            String recruitmentPipelineId
    ) {
        validateTextLengths(title, location, jobSummary, jobDescription, otherRequirements, workingHours);
        validateNumericBounds(openings, minimumExperienceYears, expectedHoursPerWeek);
        validateSalary(salaryMin, salaryMax, currency, hasPayPeriod);
        validateScreeningQuestions(screeningQuestions);
        validatePipelineTemplate(recruitmentPipelineId);
    }

    private void validateTextLengths(
            String title,
            String location,
            String jobSummary,
            String jobDescription,
            String otherRequirements,
            String workingHours
    ) {
        if (title != null && title.length() > MAX_TITLE_LENGTH) {
            throw new InvalidRequestException("Job title must be 120 characters or fewer.");
        }
        if (location != null && location.length() > MAX_LOCATION_LENGTH) {
            throw new InvalidRequestException("Location must be 120 characters or fewer.");
        }
        if (jobSummary != null && jobSummary.length() > MAX_SUMMARY_LENGTH) {
            throw new InvalidRequestException("Job summary must be 300 characters or fewer.");
        }
        if (jobDescription != null && jobDescription.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidRequestException("Job description must be 5000 characters or fewer.");
        }
        if (otherRequirements != null && otherRequirements.length() > MAX_OTHER_REQUIREMENTS_LENGTH) {
            throw new InvalidRequestException("Other requirements must be 1000 characters or fewer.");
        }
        if (workingHours != null && workingHours.length() > MAX_WORKING_HOURS_LENGTH) {
            throw new InvalidRequestException("Working hours must be 60 characters or fewer.");
        }
    }

    private void validateNumericBounds(Integer openings, Integer minExperience, BigDecimal hoursPerWeek) {
        if (openings != null && (openings < 1 || openings > 999)) {
            throw new InvalidRequestException("Number of openings must be between 1 and 999.");
        }
        if (minExperience != null && (minExperience < 0 || minExperience > MAX_EXPERIENCE_YEARS)) {
            throw new InvalidRequestException("Minimum experience must be between 0 and 50 years.");
        }
        if (hoursPerWeek != null && (hoursPerWeek.compareTo(BigDecimal.ZERO) <= 0 || hoursPerWeek.compareTo(MAX_HOURS_PER_WEEK) > 0)) {
            throw new InvalidRequestException("Expected hours per week must be between 1 and 168.");
        }
    }

    private void validateSalary(
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String currency,
            boolean hasPayPeriod
    ) {
        if (salaryMin != null && salaryMin.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("A salary can't be negative.");
        }
        if (salaryMax != null && salaryMax.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidRequestException("A salary can't be negative.");
        }
        if (salaryMin != null && salaryMax != null && salaryMax.compareTo(salaryMin) < 0) {
            throw new BusinessRuleException("The maximum salary must be at least the minimum.");
        }

        boolean hasSalary = salaryMin != null || salaryMax != null;
        if (hasSalary) {
            if (currency == null || currency.trim().isEmpty()) {
                throw new BusinessRuleException("Choose a currency for the salary range you entered.");
            }
            if (!hasPayPeriod) {
                throw new BusinessRuleException("Say whether the salary range is hourly, monthly or yearly.");
            }
        }
    }

    private void validateScreeningQuestions(List<String> screeningQuestions) {
        if (screeningQuestions != null && screeningQuestions.size() > MAX_SCREENING_QUESTIONS) {
            throw new InvalidRequestException("You may add at most 10 screening questions.");
        }
    }

    private void validatePipelineTemplate(String recruitmentPipelineId) {
        if (recruitmentPipelineId != null && !recruitmentPipelineId.trim().isEmpty()
                && !VALID_PIPELINES.contains(recruitmentPipelineId.trim())) {
            throw new BusinessRuleException("Choose one of the available recruitment pipelines.");
        }
    }
}
