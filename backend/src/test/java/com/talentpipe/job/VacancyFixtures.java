package com.talentpipe.job;

import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.EmploymentType;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.PayPeriod;
import com.talentpipe.job.entity.ShiftType;
import com.talentpipe.job.entity.VacancyContent;
import com.talentpipe.job.entity.VacancyStatus;
import com.talentpipe.job.entity.WeekDay;
import com.talentpipe.job.entity.WorkplaceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Test data for the job module.
 *
 * <p>{@link VacancyContent} and {@link JobVacancyRequest} have thirty-odd
 * components each; spelling them out in every test would bury the one value a
 * test is actually about. A test instead starts from {@link #complete()} or
 * {@link #blank()} and changes only what it is testing.</p>
 */
public final class VacancyFixtures {

    /** The fixed "today" the job tests run on (a Saturday, for no particular reason). */
    public static final LocalDate TODAY = LocalDate.of(2026, 10, 3);

    /** The instant matching {@link #TODAY}, mid-morning UTC. */
    public static final Instant NOW = Instant.parse("2026-10-03T09:00:00Z");

    private VacancyFixtures() {
        // static helpers only
    }

    /** Content that satisfies every publish rule, with a deadline 30 days after {@link #TODAY}. */
    public static ContentBuilder complete() {
        return new ContentBuilder();
    }

    /** What a brand-new draft holds: nothing but the defaults. */
    public static ContentBuilder blank() {
        return new ContentBuilder()
                .title("").department("").location("")
                .employmentType(null).workplaceType(null).applicationDeadline(null)
                .jobSummary("").jobDescription("")
                .keyResponsibilities(List.of()).requiredSkills(List.of());
    }

    /** A stored DRAFT, as a repository would hand it back: id set, version 0. */
    public static JobVacancy draft(UUID tenantId) {
        return persisted(new JobVacancy(tenantId, complete().build()));
    }

    /** A stored PUBLISHED vacancy. */
    public static JobVacancy published(UUID tenantId) {
        JobVacancy vacancy = new JobVacancy(tenantId, complete().build());
        vacancy.publish(NOW, TODAY);
        return persisted(vacancy);
    }

    /** A stored CLOSED vacancy. */
    public static JobVacancy closed(UUID tenantId) {
        JobVacancy vacancy = published(tenantId);
        vacancy.close(NOW);
        return vacancy;
    }

    /** A stored ARCHIVED vacancy. */
    public static JobVacancy archived(UUID tenantId) {
        JobVacancy vacancy = closed(tenantId);
        vacancy.archive(NOW);
        return vacancy;
    }

    /**
     * Gives an entity the fields Hibernate would assign on insert. Unit tests
     * mock the repository, so nothing else ever sets them.
     */
    public static JobVacancy persisted(JobVacancy vacancy) {
        ReflectionTestUtils.setField(vacancy, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(vacancy, "createdAt", NOW);
        ReflectionTestUtils.setField(vacancy, "updatedAt", NOW);
        return vacancy;
    }

    /** Fluent builder over the fields the tests vary. Everything else keeps its complete-advert value. */
    public static final class ContentBuilder {

        private String title = "Senior Backend Engineer";
        private String department = "Engineering";
        private int openings = 2;
        private EmploymentType employmentType = EmploymentType.FULL_TIME;
        private WorkplaceType workplaceType = WorkplaceType.HYBRID;
        private String location = "Colombo, Sri Lanka";
        private LocalDate applicationDeadline = TODAY.plusDays(30);
        private String jobSummary = "Lead the services behind our candidate pipeline.";
        private String jobDescription = "You will own the matching service.\n\nWe value pragmatic engineering.";
        private List<String> keyResponsibilities = List.of("Own the matching service");
        private List<String> requiredSkills = List.of("Java", "Spring Boot");
        private List<String> preferredSkills = List.of("Kubernetes");
        private BigDecimal salaryMin;
        private BigDecimal salaryMax;
        private String currency;
        private PayPeriod payPeriod;
        private List<String> benefits = List.of("HEALTH_INSURANCE");
        private List<WeekDay> workingDays = List.of(WeekDay.MON, WeekDay.TUE, WeekDay.WED, WeekDay.THU, WeekDay.FRI);
        private UUID assignedRecruiterId;
        private UUID hiringManagerId;
        private String recruitmentPipelineId = "TECHNICAL";

        public ContentBuilder title(String value) {
            this.title = value;
            return this;
        }

        public ContentBuilder department(String value) {
            this.department = value;
            return this;
        }

        public ContentBuilder openings(int value) {
            this.openings = value;
            return this;
        }

        public ContentBuilder employmentType(EmploymentType value) {
            this.employmentType = value;
            return this;
        }

        public ContentBuilder workplaceType(WorkplaceType value) {
            this.workplaceType = value;
            return this;
        }

        public ContentBuilder location(String value) {
            this.location = value;
            return this;
        }

        public ContentBuilder applicationDeadline(LocalDate value) {
            this.applicationDeadline = value;
            return this;
        }

        public ContentBuilder jobSummary(String value) {
            this.jobSummary = value;
            return this;
        }

        public ContentBuilder jobDescription(String value) {
            this.jobDescription = value;
            return this;
        }

        public ContentBuilder keyResponsibilities(List<String> value) {
            this.keyResponsibilities = value;
            return this;
        }

        public ContentBuilder requiredSkills(List<String> value) {
            this.requiredSkills = value;
            return this;
        }

        public ContentBuilder preferredSkills(List<String> value) {
            this.preferredSkills = value;
            return this;
        }

        public ContentBuilder salary(String min, String max) {
            this.salaryMin = min == null ? null : new BigDecimal(min);
            this.salaryMax = max == null ? null : new BigDecimal(max);
            return this;
        }

        public ContentBuilder currency(String value) {
            this.currency = value;
            return this;
        }

        public ContentBuilder payPeriod(PayPeriod value) {
            this.payPeriod = value;
            return this;
        }

        public ContentBuilder benefits(List<String> value) {
            this.benefits = value;
            return this;
        }

        public ContentBuilder workingDays(List<WeekDay> value) {
            this.workingDays = value;
            return this;
        }

        public ContentBuilder assignedRecruiterId(UUID value) {
            this.assignedRecruiterId = value;
            return this;
        }

        public ContentBuilder hiringManagerId(UUID value) {
            this.hiringManagerId = value;
            return this;
        }

        public ContentBuilder recruitmentPipelineId(String value) {
            this.recruitmentPipelineId = value;
            return this;
        }

        /** The content as the domain sees it — assumed already normalized. */
        public VacancyContent build() {
            return new VacancyContent(
                    title, department, openings, employmentType, workplaceType, location, applicationDeadline,
                    jobSummary, jobDescription, keyResponsibilities,
                    requiredSkills, preferredSkills, 4, "Bachelor’s degree",
                    List.of(), List.of("English"), null,
                    salaryMin, salaryMax, currency, payPeriod, benefits,
                    workingDays, "9:00 AM – 6:00 PM", ShiftType.DAY, new BigDecimal("40"),
                    assignedRecruiterId, hiringManagerId, recruitmentPipelineId,
                    List.of("Do you have the right to work in Sri Lanka?"));
        }

        /** The same content as a client would submit it. */
        public JobVacancyRequest toRequest(VacancyStatus status, Integer version) {
            return new JobVacancyRequest(
                    title, department, openings, employmentType, workplaceType, location, applicationDeadline,
                    jobSummary, jobDescription, keyResponsibilities,
                    requiredSkills, preferredSkills, 4, "Bachelor’s degree",
                    List.of(), List.of("English"), null,
                    salaryMin, salaryMax, currency, payPeriod, benefits,
                    workingDays, "9:00 AM – 6:00 PM", ShiftType.DAY, new BigDecimal("40"),
                    assignedRecruiterId, hiringManagerId, recruitmentPipelineId,
                    List.of("Do you have the right to work in Sri Lanka?"),
                    status, version);
        }
    }
}
