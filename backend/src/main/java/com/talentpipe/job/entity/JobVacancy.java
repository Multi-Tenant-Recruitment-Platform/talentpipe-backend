package com.talentpipe.job.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.talentpipe.job.validator.JobVacancyPublishValidator;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A job vacancy: one advert, owned by one tenant, moving through the
 * lifecycle in {@link VacancyStatus}.
 *
 * <p>This class is the state machine. It has no setters — its state changes
 * only through {@link #applyContent}, {@link #publish}, {@link #close} and
 * {@link #archive}, and each of those refuses a move the current state does
 * not allow. Two invariants therefore hold for every instance, whichever
 * service method (or future caller) reached it:</p>
 * <ol>
 *   <li>status only ever follows the arrows in {@link VacancyStatus};</li>
 *   <li>a PUBLISHED vacancy is always complete ({@link JobVacancyPublishValidator}),
 *       because both ways of becoming or staying published check it.</li>
 * </ol>
 *
 * <p>Refusals are {@link BusinessRuleException}s (422) whose message is a
 * sentence for the recruiter: the frontend shows it word for word and treats a
 * refused move as "my screen is stale".</p>
 *
 * <p>Tenancy: {@code tenantId} is a plain UUID, not a {@code @ManyToOne
 * Tenant} — the Tenant entity belongs to the tenant module and entities never
 * cross module boundaries. The same goes for the two user ids.</p>
 *
 * <p>Concurrency: {@code version} is a JPA optimistic lock. Two recruiters
 * saving the same vacancy is a real case in a shared workspace; the second
 * save fails rather than silently overwriting the first.</p>
 *
 * <p>Not mapped here: the {@code search_vector} column. PostgreSQL generates it
 * from the content columns (see V12) and only the public search query reads it.</p>
 */
@Entity
@Table(name = "job_vacancies")
public class JobVacancy extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    /** Optimistic-lock counter; starts at 0 and is bumped by every stored change. */
    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VacancyStatus status = VacancyStatus.DRAFT;

    // ------------------------------------------------------ basic information

    @Column(name = "title", nullable = false, length = VacancyContent.MAX_TITLE_LENGTH)
    private String title = "";

    @Column(name = "department", nullable = false, length = 120)
    private String department = "";

    @Column(name = "openings", nullable = false)
    private int openings = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", length = 20)
    private EmploymentType employmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "workplace_type", length = 20)
    private WorkplaceType workplaceType;

    @Column(name = "location", nullable = false, length = 120)
    private String location = "";

    /** A calendar day: applications are accepted through the end of it, tenant time. */
    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    // -------------------------------------------------------- job description

    @Column(name = "job_summary", nullable = false, length = 300)
    private String jobSummary = "";

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription = "";

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_responsibilities", nullable = false, columnDefinition = "TEXT[]")
    private List<String> keyResponsibilities = new ArrayList<>();

    // -------------------------------------------------- candidate requirements

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "required_skills", nullable = false, columnDefinition = "TEXT[]")
    private List<String> requiredSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "preferred_skills", nullable = false, columnDefinition = "TEXT[]")
    private List<String> preferredSkills = new ArrayList<>();

    @Column(name = "minimum_experience_years")
    private Integer minimumExperienceYears;

    @Column(name = "education", length = 120)
    private String education;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "certifications", nullable = false, columnDefinition = "TEXT[]")
    private List<String> certifications = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "language_requirements", nullable = false, columnDefinition = "TEXT[]")
    private List<String> languageRequirements = new ArrayList<>();

    @Column(name = "other_requirements", length = 1000)
    private String otherRequirements;

    // ------------------------------------------------------ salary & benefits

    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "currency", length = 64)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_period", length = 20)
    private PayPeriod payPeriod;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "benefits", nullable = false, columnDefinition = "TEXT[]")
    private List<String> benefits = new ArrayList<>();

    // ---------------------------------------------------------- work schedule

    /** {@link WeekDay} names, Monday first. Stored as text so the column stays a plain TEXT[]. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "working_days", nullable = false, columnDefinition = "TEXT[]")
    private List<String> workingDays = new ArrayList<>();

    @Column(name = "working_hours", length = 60)
    private String workingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", length = 20)
    private ShiftType shiftType;

    @Column(name = "expected_hours_per_week", precision = 5, scale = 2)
    private BigDecimal expectedHoursPerWeek;

    // --------------------------------------------------- recruitment settings

    @Column(name = "assigned_recruiter_id")
    private UUID assignedRecruiterId;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(name = "recruitment_pipeline_id", length = 40)
    private String recruitmentPipelineId;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "screening_questions", nullable = false, columnDefinition = "TEXT[]")
    private List<String> screeningQuestions = new ArrayList<>();

    // ---------------------------------------------------- lifecycle timestamps

    /** Set on entering each state and never cleared, so the history survives archiving. */
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // ------------------------------------------------------------ constructors

    protected JobVacancy() {
        // for JPA
    }

    /**
     * A new vacancy, always born a DRAFT. A vacancy "created as published" is
     * a draft that is published in the same transaction — one route into
     * PUBLISHED, so one place the publish rules can be skipped: none.
     */
    public JobVacancy(UUID tenantId, VacancyContent content) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        copyFrom(content);
    }

    // ----------------------------------------------------------------- content

    /**
     * Replaces the advert's content (PB-019). Status is never touched: every
     * status change has its own method below, with its own rule.
     *
     * @param today the current date in the tenant's timezone, used to judge
     *              the deadline when the vacancy is live
     * @throws BusinessRuleException (422) if the vacancy is closed or
     *         archived, or if it is published and the new content would leave
     *         it incomplete
     */
    public void applyContent(VacancyContent content, LocalDate today) {
        requireEditable();
        if (status == VacancyStatus.PUBLISHED) {
            JobVacancyPublishValidator.requireComplete(content, today, JobVacancyPublishValidator.MUST_STAY_COMPLETE);
        }
        copyFrom(content);
    }

    /**
     * Refuses an edit on a vacancy that is past editing. Exposed so a caller
     * can report "closed" before it reports anything less fundamental about
     * the same request (a stale version, say).
     *
     * @throws BusinessRuleException (422) if the vacancy is closed or archived
     */
    public void requireEditable() {
        if (!status.isEditable()) {
            throw new BusinessRuleException(
                    "This vacancy is " + status.description() + " and can no longer be edited.");
        }
    }

    /** The advert as currently stored — the inverse of {@link #applyContent}. */
    public VacancyContent content() {
        return new VacancyContent(
                title, department, openings, employmentType, workplaceType, location, applicationDeadline,
                jobSummary, jobDescription, keyResponsibilities,
                requiredSkills, preferredSkills, minimumExperienceYears, education,
                certifications, languageRequirements, otherRequirements,
                salaryMin, salaryMax, currency, payPeriod, benefits,
                workingDays.stream().map(WeekDay::valueOf).toList(),
                workingHours, shiftType, expectedHoursPerWeek,
                assignedRecruiterId, hiringManagerId, recruitmentPipelineId, screeningQuestions);
    }

    // --------------------------------------------------------------- lifecycle

    /**
     * DRAFT → PUBLISHED (PB-018): the vacancy goes onto the candidate portal.
     *
     * <p>The transition is checked before completeness, so publishing a closed
     * vacancy is refused for being closed rather than for lacking a deadline.</p>
     *
     * @param now   the moment recorded as {@code publishedAt}
     * @param today the current date in the tenant's timezone
     * @throws BusinessRuleException (422) if the vacancy is not a draft, or is
     *         a draft that is not complete enough to publish
     */
    public void publish(Instant now, LocalDate today) {
        requireTransitionTo(VacancyStatus.PUBLISHED);
        JobVacancyPublishValidator.requireComplete(content(), today, JobVacancyPublishValidator.NOT_READY_TO_PUBLISH);
        status = VacancyStatus.PUBLISHED;
        publishedAt = now;
    }

    /**
     * PUBLISHED → CLOSED (PB-020): the vacancy leaves the portal and stops
     * accepting applications. Applications already received are untouched.
     *
     * @throws BusinessRuleException (422) if the vacancy is not published
     */
    public void close(Instant now) {
        requireTransitionTo(VacancyStatus.CLOSED);
        status = VacancyStatus.CLOSED;
        closedAt = now;
    }

    /**
     * CLOSED → ARCHIVED (PB-022): the vacancy leaves the active lists. The row
     * and all three lifecycle timestamps are kept, which is what lets reports
     * still count and date it.
     *
     * @throws BusinessRuleException (422) if the vacancy is not closed
     */
    public void archive(Instant now) {
        requireTransitionTo(VacancyStatus.ARCHIVED);
        status = VacancyStatus.ARCHIVED;
        archivedAt = now;
    }

    /**
     * Whether a candidate may apply right now: the vacancy is live and its
     * deadline has not passed. The deadline day itself still counts.
     *
     * @param today the current date in the tenant's timezone
     */
    public boolean isAcceptingApplications(LocalDate today) {
        return status == VacancyStatus.PUBLISHED
                && (applicationDeadline == null || !applicationDeadline.isBefore(today));
    }

    // ----------------------------------------------------------------- getters

    public UUID getTenantId() {
        return tenantId;
    }

    public int getVersion() {
        return version;
    }

    public VacancyStatus getStatus() {
        return status;
    }

    public String getTitle() {
        return title;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    /** Safe for logs: identifiers and state only, none of the advert's text. */
    @Override
    public String toString() {
        return "JobVacancy{id=" + getId() + ", tenantId=" + tenantId + ", status=" + status + "}";
    }

    // ----------------------------------------------------------------- private

    /** @throws BusinessRuleException (422) naming the rule and the state the vacancy is really in */
    private void requireTransitionTo(VacancyStatus target) {
        if (!status.canTransitionTo(target)) {
            String current = (status == target ? "already " : "") + status.description();
            throw new BusinessRuleException(target.entryRule() + " This vacancy is " + current + ".");
        }
    }

    private void copyFrom(VacancyContent content) {
        this.title = content.title();
        this.department = content.department();
        this.openings = content.openings();
        this.employmentType = content.employmentType();
        this.workplaceType = content.workplaceType();
        this.location = content.location();
        this.applicationDeadline = content.applicationDeadline();
        this.jobSummary = content.jobSummary();
        this.jobDescription = content.jobDescription();
        this.keyResponsibilities = content.keyResponsibilities();
        this.requiredSkills = content.requiredSkills();
        this.preferredSkills = content.preferredSkills();
        this.minimumExperienceYears = content.minimumExperienceYears();
        this.education = content.education();
        this.certifications = content.certifications();
        this.languageRequirements = content.languageRequirements();
        this.otherRequirements = content.otherRequirements();
        this.salaryMin = content.salaryMin();
        this.salaryMax = content.salaryMax();
        this.currency = content.currency();
        this.payPeriod = content.payPeriod();
        this.benefits = content.benefits();
        this.workingDays = content.workingDays().stream().map(WeekDay::name).toList();
        this.workingHours = content.workingHours();
        this.shiftType = content.shiftType();
        this.expectedHoursPerWeek = content.expectedHoursPerWeek();
        this.assignedRecruiterId = content.assignedRecruiterId();
        this.hiringManagerId = content.hiringManagerId();
        this.recruitmentPipelineId = content.recruitmentPipelineId();
        this.screeningQuestions = content.screeningQuestions();
    }
}
