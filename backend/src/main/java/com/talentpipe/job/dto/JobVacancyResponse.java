package com.talentpipe.job.dto;

import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A stored vacancy as the recruiter dashboard sees it: everything submitted,
 * plus what only the server knows. Returned by every {@code /api/v1/jobs}
 * endpoint — each write answers with the vacancy <em>as now stored</em>, so
 * the frontend renders the response and never guesses.
 *
 * <p>{@code tenantId} is deliberately absent. The caller can only ever receive
 * its own tenant's vacancies, and the frontend's tenant guard treats a
 * mismatching {@code tenantId} in a payload as a breach, so leaving it out
 * keeps responses clean.</p>
 *
 * @param version        optimistic-lock counter; send it back on {@code PUT}
 * @param publishedAt    when the vacancy went live; {@code null} until then
 * @param closedAt       when it was closed; {@code null} until then
 * @param archivedAt     when it was archived; {@code null} until then
 * @param applicantCount applications received. Always {@code null} until the
 *                       applications module exists — {@code 0} would claim
 *                       that nobody applied, which nothing here can know
 */
public record JobVacancyResponse(
        UUID id,
        int version,
        VacancyStatus status,

        String title,
        String department,
        int openings,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        String location,
        LocalDate applicationDeadline,

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
        BigDecimal expectedHoursPerWeek,

        UUID assignedRecruiterId,
        UUID hiringManagerId,
        String recruitmentPipelineId,
        List<String> screeningQuestions,

        Instant createdAt,
        Instant updatedAt,
        Instant publishedAt,
        Instant closedAt,
        Instant archivedAt,
        Integer applicantCount
) {
}
