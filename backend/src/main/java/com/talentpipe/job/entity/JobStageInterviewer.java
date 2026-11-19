package com.talentpipe.job.entity;

import com.talentpipe.common.tenant.TenantFilters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

/**
 * One person on the panel of an interview round (V25).
 *
 * <p>Like {@link VacancyTeamMember}, being here decides what someone can
 * <em>see</em>: the vacancy becomes visible to them even when their role lacks
 * {@code jobs.view.all}. What they can <em>do</em> is still decided by their
 * role. The vacancy id is carried so that rule needs no join.</p>
 *
 * <p>The user is a plain id: the user entity belongs to the auth module. The
 * database keeps both in the same company (V25). A row is only ever added or
 * removed.</p>
 */
@Entity
@Table(name = "job_stage_interviewers")
// Tenant-scoped: joins the ORM tenant filter defined on User (see TenantFilterAspect).
@Filter(name = TenantFilters.TENANT_FILTER, condition = "tenant_id = :tenantId")
@IdClass(JobStageInterviewer.Key.class)
public class JobStageInterviewer {

    @Id
    @Column(name = "job_stage_id", nullable = false, updatable = false)
    private UUID jobStageId;

    @Id
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private UUID jobId;

    @Column(name = "added_by", updatable = false)
    private UUID addedBy;

    @CreationTimestamp
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected JobStageInterviewer() {
        // for JPA
    }

    public JobStageInterviewer(UUID tenantId, UUID jobId, UUID jobStageId, UUID userId, UUID addedBy) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.jobId = Objects.requireNonNull(jobId, "jobId");
        this.jobStageId = Objects.requireNonNull(jobStageId, "jobStageId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.addedBy = addedBy;
    }

    public UUID getJobStageId() {
        return jobStageId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getJobId() {
        return jobId;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    /** Composite primary key: one row per person per panel. */
    public static class Key implements Serializable {

        private UUID jobStageId;
        private UUID userId;

        protected Key() {
            // for JPA
        }

        public Key(UUID jobStageId, UUID userId) {
            this.jobStageId = jobStageId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object other) {
            return this == other
                    || (other instanceof Key key
                        && Objects.equals(jobStageId, key.jobStageId)
                        && Objects.equals(userId, key.userId));
        }

        @Override
        public int hashCode() {
            return Objects.hash(jobStageId, userId);
        }
    }
}
