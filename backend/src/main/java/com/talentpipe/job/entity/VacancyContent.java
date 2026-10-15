package com.talentpipe.job.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything a recruiter writes about a vacancy — the advert itself — as one
 * immutable value. It deliberately carries nothing about the vacancy's life:
 * no id, tenant, status, version or timestamps.
 *
 * <p>Keeping content apart from lifecycle is what makes the rest of the module
 * small. A create, an edit and a duplicate are all "put this content on a
 * vacancy", so {@link JobVacancy} has a single way to take content in
 * ({@link JobVacancy#applyContent}) and a single way to hand it out
 * ({@link JobVacancy#content()}), and the publish rules
 * ({@link com.talentpipe.job.validator.JobVacancyPublishValidator}) can judge a draft that has not been stored yet
 * exactly as they judge one that has.</p>
 *
 * <p>Instances are expected to be already normalized (trimmed, HTML-stripped,
 * de-duplicated) by {@code VacancyContentNormalizer}. The compact constructor
 * only guarantees shape: the text basics are never {@code null} (a draft holds
 * {@code ""}, matching the NOT NULL columns) and lists are never {@code null}
 * and never mutable.</p>
 */
public record VacancyContent(
        // ---- basic information
        String title,
        String department,
        int openings,
        EmploymentType employmentType,
        WorkplaceType workplaceType,
        String location,
        LocalDate applicationDeadline,

        // ---- job description
        String jobSummary,
        String jobDescription,
        List<String> keyResponsibilities,

        // ---- candidate requirements
        List<String> requiredSkills,
        List<String> preferredSkills,
        Integer minimumExperienceYears,
        String education,
        List<String> certifications,
        List<String> languageRequirements,
        String otherRequirements,

        // ---- salary & benefits
        BigDecimal salaryMin,
        BigDecimal salaryMax,
        String currency,
        PayPeriod payPeriod,
        List<String> benefits,

        // ---- work schedule
        List<WeekDay> workingDays,
        String workingHours,
        ShiftType shiftType,
        BigDecimal expectedHoursPerWeek,

        // ---- recruitment settings
        UUID assignedRecruiterId,
        UUID hiringManagerId,
        String recruitmentPipelineId,
        List<String> screeningQuestions
) {

    /** Column width of {@code job_vacancies.title}; a duplicate's title is cut to fit it. */
    public static final int MAX_TITLE_LENGTH = 120;

    /** Appended to a duplicate's title so the copy is tellable from its source in a list. */
    static final String COPY_SUFFIX = " (copy)";

    public VacancyContent {
        title = orEmpty(title);
        department = orEmpty(department);
        location = orEmpty(location);
        jobSummary = orEmpty(jobSummary);
        jobDescription = orEmpty(jobDescription);
        keyResponsibilities = copyOf(keyResponsibilities);
        requiredSkills = copyOf(requiredSkills);
        preferredSkills = copyOf(preferredSkills);
        certifications = copyOf(certifications);
        languageRequirements = copyOf(languageRequirements);
        benefits = copyOf(benefits);
        workingDays = copyOf(workingDays);
        screeningQuestions = copyOf(screeningQuestions);
    }

    /**
     * The starting point for a duplicate (PB-021): this advert as a template
     * for a new vacancy.
     *
     * <p>Everything a recruiter wrote is carried over except two things. The
     * title gains {@value #COPY_SUFFIX}, so the copy and its source can be
     * told apart before anyone renames it. The deadline is cleared: the
     * source's date is almost never right for a new round, and a copied date
     * that happens to still be valid is exactly the kind that gets published
     * unchecked.</p>
     *
     * <p>People are carried over only while they can still be assigned. A
     * recruiter who has since left the workspace would otherwise be copied
     * onto a draft that then fails the "must be an active member" rule on its
     * first save.</p>
     *
     * @param stillAssignable answers whether a user id may still be assigned
     *                        to a vacancy in this workspace
     */
    public VacancyContent asDuplicate(Predicate<UUID> stillAssignable) {
        return new VacancyContent(
                copyTitle(title), department, openings, employmentType, workplaceType, location,
                null,
                jobSummary, jobDescription, keyResponsibilities,
                requiredSkills, preferredSkills, minimumExperienceYears, education,
                certifications, languageRequirements, otherRequirements,
                salaryMin, salaryMax, currency, payPeriod, benefits,
                workingDays, workingHours, shiftType, expectedHoursPerWeek,
                keepIf(assignedRecruiterId, stillAssignable),
                keepIf(hiringManagerId, stillAssignable),
                recruitmentPipelineId, screeningQuestions);
    }

    /** "Senior Engineer" → "Senior Engineer (copy)", shortened first if the suffix would not fit. */
    private static String copyTitle(String title) {
        if (title.isBlank()) {
            return "";
        }
        int room = MAX_TITLE_LENGTH - COPY_SUFFIX.length();
        String base = title.length() > room ? title.substring(0, room).stripTrailing() : title;
        return base + COPY_SUFFIX;
    }

    private static UUID keepIf(UUID userId, Predicate<UUID> stillAssignable) {
        return userId != null && stillAssignable.test(userId) ? userId : null;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> copyOf(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
