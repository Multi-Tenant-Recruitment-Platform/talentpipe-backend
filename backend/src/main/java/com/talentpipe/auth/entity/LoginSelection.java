package com.talentpipe.auth.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A sign-in waiting for the person to say which account they meant.
 *
 * <p>Created when one email and password match several accounts. The raw
 * token goes back to the client once; only its SHA-256 hash is stored. It can
 * be exchanged exactly once, within its short lifetime, for one of the
 * accounts listed here, so the second step never needs the password again
 * and never accepts an account the password was not checked against.</p>
 *
 * <p>Does not extend {@link com.talentpipe.common.entity.BaseEntity}: same
 * rationale as {@link EmailVerificationToken}.</p>
 */
@Entity
@Table(name = "login_selections")
public class LoginSelection {

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    /**
     * A {@code users.id} or a {@code candidates.id}. Lazy on purpose: the row
     * is read with a pessimistic lock, and a join would put the lock clause on
     * the collection table too.
     */
    @ElementCollection
    @CollectionTable(name = "login_selection_accounts", joinColumns = @JoinColumn(name = "selection_id"))
    @Column(name = "account_id", nullable = false)
    private Set<UUID> accountIds = new HashSet<>();

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginSelection() {
        // for JPA
    }

    public LoginSelection(String tokenHash, Collection<UUID> accountIds, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.accountIds = new HashSet<>(accountIds);
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Set<UUID> getAccountIds() {
        return Set.copyOf(accountIds);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** True if the password was checked against this account when the choice was made. */
    public boolean offers(UUID accountId) {
        return accountIds.contains(accountId);
    }

    /** Still exchangeable: not used, and not past its expiry. */
    public boolean isRedeemable(Instant now) {
        return usedAt == null && !now.isAfter(expiresAt);
    }

    /** Marks this choice made. Idempotent: the first timestamp wins. */
    public void markUsed(Instant now) {
        if (this.usedAt == null) {
            this.usedAt = now;
        }
    }
}
