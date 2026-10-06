package com.talentpipe.job.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.job.enums.EmploymentType;
import com.talentpipe.job.enums.PayPeriod;
import com.talentpipe.job.enums.ShiftType;
import com.talentpipe.job.enums.VacancyStatus;
import com.talentpipe.job.enums.WeekDay;
import com.talentpipe.job.enums.WorkplaceType;
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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A job vacancy opened by a company for recruitment.
 *
 * <p>Persisted to {@code job_vacancies} table and strictly scoped by tenantId.
 * String lists are stored in JSONB columns.</p>
 */
@Entity
@Table(name = "job_vacancies")
public class JobVacancy extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private VacancyStatus status;

    // Section 01: Basic information
    @Column(name = "title", nullable = false, length = 120)
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

    @Column(name = "application_deadline")
    private LocalDate applicationDeadline;

    // Section 02: Job description
    @Column(name = "job_summary", nullable = false, length = 300)
    private String jobSummary = "";

    @Column(name = "job_description", nullable = false, columnDefinition = "TEXT")
    private String jobDescription = "";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_responsibilities", columnDefinition = "jsonb", nullable = false)
    private List<String> keyResponsibilities = new ArrayList<>();

    // Section 03: Candidate requirements
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb", nullable = false)
    private List<String> requiredSkills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_skills", columnDefinition = "jsonb", nullable = false)
    private List<String> preferredSkills = new ArrayList<>();

    @Column(name = "minimum_experience_years")
    private Integer minimumExperienceYears;

    @Column(name = "education", length = 120)
    private String education;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "certifications", columnDefinition = "jsonb", nullable = false)
    private List<String> certifications = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "language_requirements", columnDefinition = "jsonb", nullable = false)
    private List<String> languageRequirements = new ArrayList<>();

    @Column(name = "other_requirements", length = 1000)
    private String otherRequirements;

    // Section 04: Salary & benefits
    @Column(name = "salary_min", precision = 14, scale = 2)
    private BigDecimal salaryMin;

    @Column(name = "salary_max", precision = 14, scale = 2)
    private BigDecimal salaryMax;

    @Column(name = "currency", length = 64)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_period", length = 20)
    private PayPeriod payPeriod;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", columnDefinition = "jsonb", nullable = false)
    private List<String> benefits = new ArrayList<>();

    // Section 05: Work schedule
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "working_days", columnDefinition = "jsonb", nullable = false)
    private List<WeekDay> workingDays = new ArrayList<>();

    @Column(name = "working_hours", length = 60)
    private String workingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type", length = 20)
    private ShiftType shiftType;

    @Column(name = "expected_hours_per_week")
    private Double expectedHoursPerWeek;

    // Section 06: Recruitment settings
    @Column(name = "assigned_recruiter_id")
    private UUID assignedRecruiterId;

    @Column(name = "hiring_manager_id")
    private UUID hiringManagerId;

    @Column(name = "recruitment_pipeline_id", length = 40)
    private String recruitmentPipelineId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "screening_questions", columnDefinition = "jsonb", nullable = false)
    private List<String> screeningQuestions = new ArrayList<>();

    // Lifecycle timestamps
    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    public JobVacancy() {
        // for JPA
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public VacancyStatus getStatus() {
        return status;
    }

    public void setStatus(VacancyStatus status) {
        this.status = status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department == null ? "" : department;
    }

    public int getOpenings() {
        return openings;
    }

    public void setOpenings(int openings) {
        this.openings = openings;
    }

    public EmploymentType getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(EmploymentType employmentType) {
        this.employmentType = employmentType;
    }

    public WorkplaceType getWorkplaceType() {
        return workplaceType;
    }

    public void setWorkplaceType(WorkplaceType workplaceType) {
        this.workplaceType = workplaceType;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location == null ? "" : location;
    }

    public LocalDate getApplicationDeadline() {
        return applicationDeadline;
    }

    public void setApplicationDeadline(LocalDate applicationDeadline) {
        this.applicationDeadline = applicationDeadline;
    }

    public String getJobSummary() {
        return jobSummary;
    }

    public void setJobSummary(String jobSummary) {
        this.jobSummary = jobSummary == null ? "" : jobSummary;
    }

    public String getJobDescription() {
        return jobDescription;
    }

    public void setJobDescription(String jobDescription) {
        this.jobDescription = jobDescription == null ? "" : jobDescription;
    }

    public List<String> getKeyResponsibilities() {
        return keyResponsibilities;
    }

    public void setKeyResponsibilities(List<String> keyResponsibilities) {
        this.keyResponsibilities = keyResponsibilities == null ? new ArrayList<>() : keyResponsibilities;
    }

    public List<String> getRequiredSkills() {
        return requiredSkills;
    }

    public void setRequiredSkills(List<String> requiredSkills) {
        this.requiredSkills = requiredSkills == null ? new ArrayList<>() : requiredSkills;
    }

    public List<String> getPreferredSkills() {
        return preferredSkills;
    }

    public void setPreferredSkills(List<String> preferredSkills) {
        this.preferredSkills = preferredSkills == null ? new ArrayList<>() : preferredSkills;
    }

    public Integer getMinimumExperienceYears() {
        return minimumExperienceYears;
    }

    public void setMinimumExperienceYears(Integer minimumExperienceYears) {
        this.minimumExperienceYears = minimumExperienceYears;
    }

    public String getEducation() {
        return education;
    }

    public void setEducation(String education) {
        this.education = education;
    }

    public List<String> getCertifications() {
        return certifications;
    }

    public void setCertifications(List<String> certifications) {
        this.certifications = certifications == null ? new ArrayList<>() : certifications;
    }

    public List<String> getLanguageRequirements() {
        return languageRequirements;
    }

    public void setLanguageRequirements(List<String> languageRequirements) {
        this.languageRequirements = languageRequirements == null ? new ArrayList<>() : languageRequirements;
    }

    public String getOtherRequirements() {
        return otherRequirements;
    }

    public void setOtherRequirements(String otherRequirements) {
        this.otherRequirements = otherRequirements;
    }

    public BigDecimal getSalaryMin() {
        return salaryMin;
    }

    public void setSalaryMin(BigDecimal salaryMin) {
        this.salaryMin = salaryMin;
    }

    public BigDecimal getSalaryMax() {
        return salaryMax;
    }

    public void setSalaryMax(BigDecimal salaryMax) {
        this.salaryMax = salaryMax;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PayPeriod getPayPeriod() {
        return payPeriod;
    }

    public void setPayPeriod(PayPeriod payPeriod) {
        this.payPeriod = payPeriod;
    }

    public List<String> getBenefits() {
        return benefits;
    }

    public void setBenefits(List<String> benefits) {
        this.benefits = benefits == null ? new ArrayList<>() : benefits;
    }

    public List<WeekDay> getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(List<WeekDay> workingDays) {
        this.workingDays = workingDays == null ? new ArrayList<>() : workingDays;
    }

    public String getWorkingHours() {
        return workingHours;
    }

    public void setWorkingHours(String workingHours) {
        this.workingHours = workingHours;
    }

    public ShiftType getShiftType() {
        return shiftType;
    }

    public void setShiftType(ShiftType shiftType) {
        this.shiftType = shiftType;
    }

    public Double getExpectedHoursPerWeek() {
        return expectedHoursPerWeek;
    }

    public void setExpectedHoursPerWeek(Double expectedHoursPerWeek) {
        this.expectedHoursPerWeek = expectedHoursPerWeek;
    }

    public UUID getAssignedRecruiterId() {
        return assignedRecruiterId;
    }

    public void setAssignedRecruiterId(UUID assignedRecruiterId) {
        this.assignedRecruiterId = assignedRecruiterId;
    }

    public UUID getHiringManagerId() {
        return hiringManagerId;
    }

    public void setHiringManagerId(UUID hiringManagerId) {
        this.hiringManagerId = hiringManagerId;
    }

    public String getRecruitmentPipelineId() {
        return recruitmentPipelineId;
    }

    public void setRecruitmentPipelineId(String recruitmentPipelineId) {
        this.recruitmentPipelineId = recruitmentPipelineId;
    }

    public List<String> getScreeningQuestions() {
        return screeningQuestions;
    }

    public void setScreeningQuestions(List<String> screeningQuestions) {
        this.screeningQuestions = screeningQuestions == null ? new ArrayList<>() : screeningQuestions;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
