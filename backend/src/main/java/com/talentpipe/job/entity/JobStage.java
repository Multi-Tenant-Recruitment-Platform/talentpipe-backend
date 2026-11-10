package com.talentpipe.job.entity;

import com.talentpipe.common.tenant.TenantFilters;
import com.talentpipe.pipeline.entity.StageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;

/**
 * One stage of the pipeline a vacancy runs on: a copy of a pipeline template's
 * stage, taken when the vacancy was created (V23).
 *
 * <p>The copy is what decouples a vacancy from its template — editing,
 * archiving or deleting the template later changes nothing here — so a stage
 * is written once and never changed. {@link Immutable} makes Hibernate ignore
 * any change to a loaded instance, and the class has no setters.</p>
 *
 * <p>The vacancy is a plain id, as on {@link VacancyTeamMember}; the database
 * keeps both in the same tenant (composite foreign key, V23). The stage type
 * is the pipeline module's vocabulary, because the copy must mean exactly what
 * the template meant.</p>
 *
 * <p>Does not extend {@link com.talentpipe.common.entity.BaseEntity}: a row
 * that is never updated has no {@code updated_at}.</p>
 */
@Entity
@Table(name = "job_stages")
@Immutable
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
public class JobStage {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, updatable = false, length = 30)
    private StageType stageType;

    @Column(name = "name", nullable = false, updatable = false, length = 100)
    private String name;

    /** Interview rounds only; {@code null} for every other stage type. */
    @Column(name = "benchmark", updatable = false)
    private Integer benchmark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobStage() {
        // for JPA
    }

    public JobStage(UUID tenantId, UUID jobId, int position, StageType stageType, String name, Integer benchmark) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.position = position;
        this.stageType = Objects.requireNonNull(stageType, "stageType");
        this.name = Objects.requireNonNull(name, "name");
        this.benchmark = benchmark;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getPosition() {
        return position;
    }

    public StageType getStageType() {
        return stageType;
    }

    public String getName() {
        return name;
    }

    public Integer getBenchmark() {
        return benchmark;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
