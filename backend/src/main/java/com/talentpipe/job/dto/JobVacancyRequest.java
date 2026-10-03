package com.talentpipe.job.dto;

import com.talentpipe.common.util.ValidTaxonomyList;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.job.entity.WorkplaceType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request body for both {@code POST /api/v1/jobs} (create) and
 * {@code PUT /api/v1/jobs/{id}} (edit).
 *
 * <p>The two operations carry the same thirty content fields and differ in one
 * field each, so they share this record instead of two near-identical copies.
 * The difference is expressed with validation groups:</p>
 * <ul>
 *   <li>{@link OnCreate} — {@code status} is required and says whether the
 *       vacancy is filed as a DRAFT or published at once.</li>
 *   <li>{@link OnUpdate} — {@code version} is required: the optimistic-lock
 *       token from the response being edited. {@code status} is ignored on an
 *       edit, because every status change has its own endpoint and a PUT that
 *       could also flip status would be a second, unguarded route to it.</li>
 * </ul>
 *
 * <h3>What is validated here, and what is not</h3>
 * <p>The constraints below are <em>shape</em> rules — lengths, ranges, list
 * sizes — and hold in every state, drafts included. Whether a field is
 * <em>required</em> depends on the vacancy's state and is decided later by
 * {@code VacancyPublishRules}: a draft is a partial vacancy by definition, so
 * nothing here is {@code @NotBlank}. Rules that span fields (salary pairing,
 * assignees) are business rules and live in {@code VacancyContentRules}.</p>
 *
 * <p>Messages are sentences, because the frontend shows a 400 to the recruiter
 * as written.</p>
 *
 * <p>The tenant is deliberately absent — it comes from the access token only.
 * An edit is a full replacement: a {@code null} field clears the value.</p>
 */
public record JobVacancyRequest(

        // ---- basic information
        @Size(max = 120, message = "Keep the job title to 120 characters or fewer.")
        String title,

        @Size(max = 120, message = "Keep the department to 120 characters or fewer.")
        String department,

        @NotNull(message = "Enter how many people you are hiring.")
        @Min(value = 1, message = "Enter at least one opening.")
        @Max(value = 999, message = "Enter 999 openings or fewer.")
        Integer openings,

        EmploymentType employmentType,

        WorkplaceType workplaceType,

        @Size(max = 120, message = "Keep the location to 120 characters or fewer.")
        String location,

        // ISO date (YYYY-MM-DD). A day, not an instant; may be in the past on a draft.
        LocalDate applicationDeadline,

        // ---- job description
        @Size(max = 300, message = "Keep the job summary to 300 characters or fewer.")
        String jobSummary,

        @Size(max = 5000, message = "Keep the job description to 5,000 characters or fewer.")
        String jobDescription,

        @ValidTaxonomyList(maxSize = 30, maxEntryLength = 300,
                message = "List up to 30 key responsibilities, each 300 characters or fewer.")
        List<String> keyResponsibilities,

        // ---- candidate requirements
        @ValidTaxonomyList(maxSize = 50, maxEntryLength = 100,
                message = "List up to 50 required skills, each 100 characters or fewer.")
        List<String> requiredSkills,

        @ValidTaxonomyList(maxSize = 50, maxEntryLength = 100,
                message = "List up to 50 preferred skills, each 100 characters or fewer.")
        List<String> preferredSkills,

        @Min(value = 0, message = "Minimum experience can't be negative.")
        @Max(value = 50, message = "Enter 50 years of experience or fewer.")
        Integer minimumExperienceYears,

        @Size(max = 120, message = "Keep the education requirement to 120 characters or fewer.")
        String education,

        @ValidTaxonomyList(maxSize = 30, maxEntryLength = 150,
                message = "List up to 30 certifications, each 150 characters or fewer.")
        List<String> certifications,

        @ValidTaxonomyList(maxSize = 20, maxEntryLength = 100,
                message = "List up to 20 language requirements, each 100 characters or fewer.")
        List<String> languageRequirements,

        @Size(max = 1000, message = "Keep other requirements to 1,000 characters or fewer.")
        String otherRequirements,

        // ---- salary & benefits
        @DecimalMin(value = "0", message = "A salary can't be negative.")
        @Digits(integer = 12, fraction = 2, message = "Enter the minimum salary with at most two decimal places.")
        BigDecimal salaryMin,

        @DecimalMin(value = "0", message = "A salary can't be negative.")
        @Digits(integer = 12, fraction = 2, message = "Enter the maximum salary with at most two decimal places.")
        BigDecimal salaryMax,

        @Size(max = 64, message = "Keep the currency to 64 characters or fewer.")
        String currency,

        PayPeriod payPeriod,

        // Identifiers from the company-profile benefits catalogue; membership is
        // checked in VacancyContentNormalizer, which also canonicalizes them.
        @ValidTaxonomyList(maxSize = 30, maxEntryLength = 60,
                message = "List up to 30 benefits from the benefits catalogue.")
        List<String> benefits,

        // ---- work schedule
        @Size(max = 7, message = "A week has seven working days at most.")
        List<WeekDay> workingDays,

        @Size(max = 60, message = "Keep working hours to 60 characters or fewer.")
        String workingHours,

        ShiftType shiftType,

        @DecimalMin(value = "0", inclusive = false, message = "Expected hours per week must be more than zero.")
        @DecimalMax(value = "168", message = "There are only 168 hours in a week.")
        @Digits(integer = 3, fraction = 2, message = "Enter expected hours per week with at most two decimal places.")
        BigDecimal expectedHoursPerWeek,

        // ---- recruitment settings
        UUID assignedRecruiterId,

        UUID hiringManagerId,

        @Size(max = 40, message = "Choose one of the available recruitment pipelines.")
        String recruitmentPipelineId,

        @ValidTaxonomyList(maxSize = 10, maxEntryLength = 300,
                message = "Ask at most 10 screening questions, each 300 characters or fewer.")
        List<String> screeningQuestions,

        // ---- operation-specific
        @NotNull(groups = OnCreate.class,
                message = "Choose whether to save this vacancy as a draft or publish it.")
        VacancyStatus status,

        @NotNull(groups = OnUpdate.class,
                message = "The vacancy's version is required to save changes. Reload the page and try again.")
        @PositiveOrZero(groups = OnUpdate.class,
                message = "The vacancy's version is not valid. Reload the page and try again.")
        Integer version
) {

    /** Validation group for {@code POST /jobs}: {@code status} is required. */
    public interface OnCreate {
    }

    /** Validation group for {@code PUT /jobs/{id}}: {@code version} is required. */
    public interface OnUpdate {
    }
}
