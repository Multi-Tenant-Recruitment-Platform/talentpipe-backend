package com.talentpipe.demo;

import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One demo advert, written the way a recruiter would fill in the vacancy
 * form, and how far along its life it should end up. Defaults cover a
 * standard full-time, on-site, weekday role so each advert only states what
 * makes it different.
 */
final class DemoVacancy {

    private static final List<WeekDay> WEEKDAYS =
            List.of(WeekDay.MON, WeekDay.TUE, WeekDay.WED, WeekDay.THU, WeekDay.FRI);

    final String title;
    final VacancyStatus finalStatus;
    /** How long ago it was filed; it went live a day later. */
    final int daysAgo;

    private String department = "";
    private String industry;
    private int openings = 1;
    private EmploymentType employmentType = EmploymentType.FULL_TIME;
    private WorkplaceType workplaceType = WorkplaceType.ON_SITE;
    private String location = "";
    private int deadlineInDays = 30;
    private String summary = "";
    private String description = "";
    private List<String> responsibilities = List.of();
    private List<String> requiredSkills = List.of();
    private List<String> preferredSkills = List.of();
    private Integer minimumYears;
    private String education;
    private List<String> certifications = List.of();
    private List<String> languages = List.of("English");
    private String otherRequirements;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private String currency;
    private PayPeriod payPeriod;
    private List<String> benefits = List.of();
    private List<WeekDay> workingDays = WEEKDAYS;
    private String workingHours = "8:30 AM – 5:30 PM";
    private ShiftType shiftType = ShiftType.DAY;
    private BigDecimal hoursPerWeek = new BigDecimal("45");
    private String pipeline = "STANDARD";
    private List<String> screeningQuestions = List.of();
    private Boolean aiScreening;
    private Boolean candidateMatching;

    UUID recruiter;
    UUID hiringManager;
    final List<UUID> team = new ArrayList<>();

    private DemoVacancy(String title, VacancyStatus finalStatus, int daysAgo) {
        this.title = title;
        this.finalStatus = finalStatus;
        this.daysAgo = daysAgo;
    }

    static DemoVacancy live(String title, int daysAgo) {
        return new DemoVacancy(title, VacancyStatus.PUBLISHED, daysAgo);
    }

    static DemoVacancy draft(String title, int daysAgo) {
        return new DemoVacancy(title, VacancyStatus.DRAFT, daysAgo);
    }

    static DemoVacancy closed(String title, int daysAgo) {
        return new DemoVacancy(title, VacancyStatus.CLOSED, daysAgo);
    }

    static DemoVacancy archived(String title, int daysAgo) {
        return new DemoVacancy(title, VacancyStatus.ARCHIVED, daysAgo);
    }

    // ------------------------------------------------------------- the advert

    DemoVacancy in(String department, String location) {
        this.department = department;
        this.location = location;
        return this;
    }

    DemoVacancy industry(String industry) {
        this.industry = industry;
        return this;
    }

    DemoVacancy openings(int openings) {
        this.openings = openings;
        return this;
    }

    DemoVacancy as(EmploymentType employmentType, WorkplaceType workplaceType) {
        this.employmentType = employmentType;
        this.workplaceType = workplaceType;
        return this;
    }

    DemoVacancy closesInDays(int days) {
        this.deadlineInDays = days;
        return this;
    }

    DemoVacancy about(String summary, String description) {
        this.summary = summary;
        this.description = description;
        return this;
    }

    DemoVacancy responsibilities(String... responsibilities) {
        this.responsibilities = List.of(responsibilities);
        return this;
    }

    DemoVacancy skills(List<String> required, List<String> preferred) {
        this.requiredSkills = required;
        this.preferredSkills = preferred;
        return this;
    }

    DemoVacancy experience(int years, String education) {
        this.minimumYears = years;
        this.education = education;
        return this;
    }

    DemoVacancy certifications(String... certifications) {
        this.certifications = List.of(certifications);
        return this;
    }

    DemoVacancy languages(String... languages) {
        this.languages = List.of(languages);
        return this;
    }

    DemoVacancy otherRequirements(String otherRequirements) {
        this.otherRequirements = otherRequirements;
        return this;
    }

    DemoVacancy pays(String currency, long min, Long max, PayPeriod payPeriod) {
        this.currency = currency;
        this.salaryMin = BigDecimal.valueOf(min);
        this.salaryMax = max == null ? null : BigDecimal.valueOf(max);
        this.payPeriod = payPeriod;
        return this;
    }

    DemoVacancy benefits(List<String> benefits) {
        this.benefits = benefits;
        return this;
    }

    DemoVacancy schedule(List<WeekDay> days, String hours, ShiftType shift, int hoursPerWeek) {
        this.workingDays = days;
        this.workingHours = hours;
        this.shiftType = shift;
        this.hoursPerWeek = BigDecimal.valueOf(hoursPerWeek);
        return this;
    }

    DemoVacancy pipeline(String pipeline) {
        this.pipeline = pipeline;
        return this;
    }

    DemoVacancy screening(String... questions) {
        this.screeningQuestions = List.of(questions);
        return this;
    }

    DemoVacancy switches(boolean aiScreening, boolean candidateMatching) {
        this.aiScreening = aiScreening;
        this.candidateMatching = candidateMatching;
        return this;
    }

    // -------------------------------------------------------------- the people

    DemoVacancy recruitedBy(UUID recruiter, UUID hiringManager) {
        this.recruiter = recruiter;
        this.hiringManager = hiringManager;
        return this;
    }

    DemoVacancy withTeamMembers(UUID... members) {
        this.team.addAll(List.of(members));
        return this;
    }

    /** The vacancy form as a draft: it is published, if at all, by the lifecycle endpoint. */
    JobVacancyRequest toRequest(LocalDate today) {
        return new JobVacancyRequest(
                title, department, industry, openings, employmentType, workplaceType, location,
                today.plusDays(deadlineInDays),
                summary, description, responsibilities,
                requiredSkills, preferredSkills, minimumYears, education,
                certifications, languages, otherRequirements,
                salaryMin, salaryMax, currency, payPeriod, benefits,
                workingDays, workingHours, shiftType, hoursPerWeek,
                recruiter, hiringManager, pipeline, screeningQuestions,
                aiScreening, candidateMatching,
                VacancyStatus.DRAFT, null);
    }
}
