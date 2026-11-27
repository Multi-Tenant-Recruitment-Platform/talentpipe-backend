package com.talentpipe.job.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Filter;

/**
 * One of the things a vacancy's applications are scored on, and how much it
 * counts (V26).
 *
 * <p>The database keeps a criterion in the same company as its vacancy
 * (composite foreign key, V26), its weight above 0 and at most 1, and its name
 * unique within the vacancy.</p>
 */
@Entity
@Table(name = "job_screening_criteria")
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class JobScreeningCriteria extends BaseEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    /** The share of the 100%, as a fraction above 0 and at most 1; {@code null} while a draft has not decided it (V27). */
    @Column(name = "weight", precision = 4, scale = 3)
    private BigDecimal weight;

    /** What the criterion is about (V27). */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private MatchingCategory category;

    /** Must-have or preferred (V27); every criterion says which since skills alone are weighted (V28). */
    @Enumerated(EnumType.STRING)
    @Column(name = "requirement", nullable = false, length = 20)
    private MatchingRequirement requirement;

    /** The order the recruiter put the criteria in (V27). */
    @Column(name = "position", nullable = false)
    private int position;

    protected JobScreeningCriteria() {
        // for JPA
    }

    /** A technical skill the candidate would ideally have — the shape V26 stored. */
    public JobScreeningCriteria(UUID tenantId, UUID jobId, String name, BigDecimal weight) {
        this(tenantId, jobId, MatchingCategory.TECHNICAL_SKILL, name, Objects.requireNonNull(weight, "weight"),
                MatchingRequirement.PREFERRED, 0);
    }

    /**
     * @param weight      the share of the 100%, as a fraction above 0 and at most 1;
     *                    {@code null} while a draft has not decided it
     * @param requirement must-have or preferred
     */
    public JobScreeningCriteria(UUID tenantId, UUID jobId, MatchingCategory category, String name,
                                BigDecimal weight, MatchingRequirement requirement, int position) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.category = Objects.requireNonNull(category, "category");
        this.name = Objects.requireNonNull(name, "name");
        this.weight = weight;
        this.requirement = Objects.requireNonNull(requirement, "requirement");
        this.position = position;
    }

    public MatchingCategory getCategory() {
        return category;
    }

    public MatchingRequirement getRequirement() {
        return requirement;
    }

    public int getPosition() {
        return position;
    }

    public UUID getJobId() {
        return jobId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getWeight() {
        return weight;
    }
}
