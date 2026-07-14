package com.talentpipe.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Shared base for all UUID-keyed, audited entities.
 *
 * <p>The UUID is generated application-side (not by the database) so an entity
 * has a stable identity before its first flush, and timestamps are maintained
 * by Hibernate on insert/update to mirror the {@code created_at}/{@code
 * updated_at} column defaults defined in the Flyway migrations.</p>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Identity-based equality: two entities are equal iff they share the same
     * persistent id. Transient (unsaved, id == null) entities are only equal
     * to themselves.
     */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        // Constant-per-class hash keeps the contract stable across the
        // transient -> persistent transition (a known JPA equality pitfall).
        return Objects.hashCode(getClass());
    }
}
