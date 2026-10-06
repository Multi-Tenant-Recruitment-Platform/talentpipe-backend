package com.talentpipe.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.ShiftType;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Payload for creating a job vacancy.
 */
public record JobVacancyRequest(
        String title,
        String department,
        Integer openings,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        String location,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate applicationDeadline,
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
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        PayPeriod payPeriod,
        List<String> benefits,
        List<WeekDay> workingDays,
        String workingHours,
        ShiftType shiftType,
        Double expectedHoursPerWeek,
        UUID assignedRecruiterId,
        UUID hiringManagerId,
        String recruitmentPipelineId,
        List<String> screeningQuestions,
        VacancyStatus status
) {
}
