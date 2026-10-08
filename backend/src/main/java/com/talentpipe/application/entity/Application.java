package com.talentpipe.application.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "applications")
public class Application extends BaseEntity {

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    protected Application() {
        // for JPA
    }

    public Application(UUID jobId, UUID candidateId, UUID tenantId) {
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.tenantId = tenantId;
    }

    public UUID getJobId() { return jobId; }
    public UUID getCandidateId() { return candidateId; }
    public UUID getTenantId() { return tenantId; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getWithdrawnAt() { return withdrawnAt; }

    public void changeStatus(ApplicationStatus status) {
        this.status = status;
        this.withdrawnAt = status == ApplicationStatus.WITHDRAWN ? Instant.now() : null;
    }
}
