package com.talentpipe.candidate.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit record tracking candidate interactions with the Talent Pool.
 *
 * <p>Preserves an append-only timeline of when candidates join or leave the
 * pool, and which CV was active at each point in time.</p>
 */
@Entity
@Table(name = "candidate_talent_pool_audits")
public class CandidateTalentPoolAudit extends BaseEntity {

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "event_type", nullable = false, length = 30)
    private String eventType;

    @Column(name = "joined_pool", nullable = false)
    private boolean joinedPool;

    @Column(name = "cv_id")
    private UUID cvId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt = Instant.now();

    protected CandidateTalentPoolAudit() {
        // for JPA
    }

    public CandidateTalentPoolAudit(UUID candidateId, String eventType, boolean joinedPool, UUID cvId) {
        this.candidateId = candidateId;
        this.eventType = eventType;
        this.joinedPool = joinedPool;
        this.cvId = cvId;
        this.occurredAt = Instant.now();
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getEventType() {
        return eventType;
    }

    public boolean isJoinedPool() {
        return joinedPool;
    }

    public UUID getCvId() {
        return cvId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
