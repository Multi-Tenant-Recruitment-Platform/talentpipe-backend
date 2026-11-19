package com.talentpipe.job.entity;

import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * The panel of one interview round of a vacancy: how many interviewers the
 * round needs (V25). The people on it are {@link JobStageInterviewer} rows.
 *
 * <p>Lives beside {@link JobStage} rather than in it: a stage is a copy of the
 * template's, written once, while the panel changes as the vacancy is worked
 * on. The database keeps a panel on an interview stage of the same vacancy and
 * company (composite foreign key, V25).</p>
 *
 * <p>Keyed by the stage it belongs to: a stage has one panel at most.</p>
 */
@Entity
@Table(name = "job_stage_panels")
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
public class JobStagePanel {

    /** The only stage type a panel may hang off; stored so the V25 foreign key can check it. */
    private static final String INTERVIEW = "INTERVIEW";

    @Id
    @Column(name = "job_stage_id", nullable = false, updatable = false)
    private UUID jobStageId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "stage_type", nullable = false, updatable = false, length = 30)
    private String stageType = INTERVIEW;

    @Column(name = "interviewer_count", nullable = false)
    private int interviewerCount;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected JobStagePanel() {
        // for JPA
    }

    public JobStagePanel(UUID tenantId, UUID jobId, UUID jobStageId, int interviewerCount, UUID updatedBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.jobStageId = Objects.requireNonNull(jobStageId, "jobStageId");
        this.interviewerCount = interviewerCount;
        this.updatedBy = updatedBy;
    }

    /** Changes how many interviewers the round needs. */
    public void resize(int interviewerCount, UUID updatedBy) {
        this.interviewerCount = interviewerCount;
        this.updatedBy = updatedBy;
    }

    public UUID getJobStageId() {
        return jobStageId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public int getInterviewerCount() {
        return interviewerCount;
    }

    public UUID getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
