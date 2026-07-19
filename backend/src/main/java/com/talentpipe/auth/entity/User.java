package com.talentpipe.auth.entity;

import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A platform staff account (company admins, HR managers, interviewers,
 * super admins). Candidates are NOT users — they get their own
 * tenant-independent model in a later sprint.
 *
 * <p>Tenancy: {@code tenantId} is a plain UUID column, not a {@code @ManyToOne
 * Tenant} — the Tenant entity belongs to the tenant module and entities never
 * cross module boundaries. {@code null} tenantId identifies a SUPER_ADMIN
 * platform operator. Email is unique per tenant, not globally.</p>
 *
 * <p>Security: {@code passwordHash} (bcrypt, cost 12) must never be exposed
 * through a DTO, log statement or toString.</p>
 */
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    /** Maintained from Week 2 on (account lockout); column exists now to avoid a migration. */
    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    /** Enforced from Week 2 on (account lockout); column exists now to avoid a migration. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {
        // for JPA
    }

    public User(UUID tenantId, Role role, String email, String passwordHash,
                String firstName, String lastName, UserStatus status) {
        this.tenantId = tenantId;
        this.role = role;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Role getRole() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserStatus getStatus() {
        return status;
    }

    public short getFailedLoginCount() {
        return failedLoginCount;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /**
     * Used by {@link com.talentpipe.auth.service.PasswordResetService} after a
     * successful password reset. The new value must already be hashed by the
     * caller (bcrypt cost 12).
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Used by {@link com.talentpipe.auth.service.EmailVerificationService} to activate an account. */
    public void setStatus(UserStatus status) {
        this.status = status;
    }

    /** Deliberately excludes email and passwordHash — safe for logs. */
    @Override
    public String toString() {
        return "User{id=" + getId() + ", tenantId=" + tenantId + ", status=" + status + "}";
    }
}
