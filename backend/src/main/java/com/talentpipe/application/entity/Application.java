package com.talentpipe.application.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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

    /**
     * The candidate's CV as it was when they applied, so that replacing it later
     * never changes this application. Null when they had none, or applied before V26.
     */
    @Column(name = "cv_id", updatable = false)
    private UUID cvId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    /** Where the recruiter's review stands (V29); see {@link ReviewStatus}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 30)
    private ReviewStatus reviewStatus = ReviewStatus.PENDING_SCREENING;

    /** The stage of the vacancy's pipeline the applicant is in; null when the vacancy has none. */
    @Column(name = "current_stage_id")
    private UUID currentStageId;

    /** The latest recruiter decision; null until one. */
    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    /** Optimistic lock: a recruiter acting on a stale copy, or the screener racing one, gets a 409. */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Application() {
        // for JPA
    }

    public Application(UUID jobId, UUID candidateId, UUID tenantId, UUID cvId) {
        this.jobId = jobId;
        this.candidateId = candidateId;
        this.tenantId = tenantId;
        this.cvId = cvId;
    }

    public UUID getJobId() { return jobId; }
    public UUID getCandidateId() { return candidateId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getCvId() { return cvId; }
    public ApplicationStatus getStatus() { return status; }
    public Instant getWithdrawnAt() { return withdrawnAt; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public UUID getCurrentStageId() { return currentStageId; }
    public Instant getDecidedAt() { return decidedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public long getVersion() { return version; }

    @Override
    public boolean equals(Object other) {
        return super.equals(other);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    /**
     * The older status change (candidate withdrawal, {@code PATCH .../status}).
     * Keeps the review status in step, so the dashboard agrees with what the
     * candidate sees: a rejection, shortlisting or withdrawal is a decision;
     * moving back to APPLIED or IN_REVIEW is not, and leaves the review alone.
     *
     * @param actorUserId the recruiter who decided; null for the candidate's own withdrawal
     */
    public void changeStatus(ApplicationStatus status, UUID actorUserId, Instant at) {
        this.status = status;
        this.withdrawnAt = status == ApplicationStatus.WITHDRAWN ? at : null;
        ReviewStatus decided = switch (status) {
            case REJECTED -> ReviewStatus.REJECTED;
            case SHORTLISTED -> ReviewStatus.ADVANCED_BY_RECRUITER;
            case WITHDRAWN -> ReviewStatus.WITHDRAWN;
            case APPLIED, IN_REVIEW -> null;
        };
        if (decided != null) {
            this.reviewStatus = decided;
            if (actorUserId != null) {
                this.decidedAt = at;
                this.decidedBy = actorUserId;
            }
        }
    }

    /** {@link #changeStatus(ApplicationStatus, UUID, Instant)} with no recruiter, now. */
    public void changeStatus(ApplicationStatus status) {
        changeStatus(status, null, Instant.now());
    }

    /** Puts a new application at the first stage of its vacancy's pipeline. */
    public void startAt(UUID firstStageId) {
        this.currentStageId = firstStageId;
    }

    /**
     * A recruiter sends an applicant waiting for manual review on to interview.
     * The candidate sees "Shortlisted".
     *
     * @param interviewStageId the vacancy's first interview stage; null when it has no pipeline
     * @throws IllegalStateException unless the applicant is waiting for manual review
     */
    public void advanceToInterview(UUID interviewStageId, UUID actorUserId, Instant at) {
        if (reviewStatus != ReviewStatus.MANUAL_REVIEW) {
            throw new IllegalStateException("Only an applicant waiting for manual review can be sent to interview");
        }
        this.reviewStatus = ReviewStatus.ADVANCED_BY_RECRUITER;
        this.status = ApplicationStatus.SHORTLISTED;
        if (interviewStageId != null) {
            this.currentStageId = interviewStageId;
        }
        this.decidedAt = at;
        this.decidedBy = actorUserId;
    }

    /**
     * Records the verdict of a screening. A decision a person has already
     * taken stands; only an application still waiting is moved.
     *
     * @param screeningStageId the vacancy's screening stage, if it has one
     * @return whether the review status changed
     */
    public boolean applyScreening(ReviewStatus verdict, UUID screeningStageId) {
        if (reviewStatus != ReviewStatus.PENDING_SCREENING) {
            return false;
        }
        this.reviewStatus = verdict;
        if (screeningStageId != null) {
            this.currentStageId = screeningStageId;
        }
        return true;
    }
}
