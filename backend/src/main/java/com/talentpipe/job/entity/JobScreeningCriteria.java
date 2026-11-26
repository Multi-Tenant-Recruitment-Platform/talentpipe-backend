package com.talentpipe.job.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "weight", nullable = false, precision = 4, scale = 3)
    private BigDecimal weight;

    protected JobScreeningCriteria() {
        // for JPA
    }

    public JobScreeningCriteria(UUID tenantId, UUID jobId, String name, BigDecimal weight) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.name = Objects.requireNonNull(name, "name");
        this.weight = Objects.requireNonNull(weight, "weight");
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
