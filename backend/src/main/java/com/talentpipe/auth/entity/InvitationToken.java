package com.talentpipe.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A single-use invitation token (PB-003 / PB-004). Only the SHA-256 hash is
 * stored; the raw token exists only in the emailed link. Default TTL is 7 days,
 * enforced at the service layer.
 *
 * <p>The invitee's tenant and role live on their INVITED {@code users} row, so
 * this table needs neither — accepting the invitation flips that row to ACTIVE
 * with the tenant and role it was created with, which is why neither can be
 * influenced by the accept request.</p>
 *
 * <p>Does not extend {@code BaseEntity}: no {@code updated_at} column (rows are
 * immutable after creation except the single {@code used_at} write).</p>
 */
@Entity
@Table(name = "invitation_tokens")
public class InvitationToken {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected InvitationToken() {
        // for JPA
    }

    public InvitationToken(UUID userId, String tokenHash, Instant expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /** Marks this token consumed. Idempotent: the first timestamp wins. */
    public void markUsed(Instant now) {
        if (this.usedAt == null) {
            this.usedAt = now;
        }
    }
}
