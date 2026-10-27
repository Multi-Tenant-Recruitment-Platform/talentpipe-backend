package com.talentpipe.auth.entity;

import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.util.LockoutPolicy;
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
 * A platform staff account: a member of a company workspace, or a platform
 * operator (SUPER_ADMIN). Candidates are NOT users — they have their own
 * tenant-independent model in the candidate module.
 *
 * <p>Roles: a company member's access comes from {@link #getTenantRole()} —
 * the company-defined role they hold. {@link #getRole()} is the old fixed role
 * and is now only a transitional mirror: set for platform operators and for
 * members of the three default roles (Owner → COMPANY_ADMIN, Recruiter →
 * HR_MANAGER, Interviewer → INTERVIEWER), {@code null} for members of a
 * custom role. Nothing new should read it. The database enforces that every
 * company member has a company role, and that it belongs to their own
 * company.</p>
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
@SuppressWarnings("java:S2160") // Entity identity is defined solely by persistent ID in BaseEntity
public class User extends BaseEntity {

    /** {@link #legacyRoleName()} for a member of a role the company defined itself. */
    public static final String CUSTOM_ROLE = "CUSTOM";

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    /** Transitional mirror of the old fixed role; see the class comment. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id")
    private Role role;

    /** The company role this member holds; {@code null} only for platform operators. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_role_id")
    private TenantRole tenantRole;

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

    /** Consecutive failed logins; reset to zero on success (brute-force protection). */
    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    /** Set when the failure threshold is hit; logins are refused until it passes. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected User() {
        // for JPA
    }

    /**
     * A user identified by a fixed role only — a platform operator. Company
     * members are created with {@link #User(UUID, TenantRole, Role, String,
     * String, String, String, UserStatus)} so they always have a company role.
     */
    public User(UUID tenantId, Role role, String email, String passwordHash,
                String firstName, String lastName, UserStatus status) {
        this(tenantId, null, role, email, passwordHash, firstName, lastName, status);
    }

    /**
     * A company member.
     *
     * @param legacyRole the fixed role mirroring {@code tenantRole}, or
     *                   {@code null} when it is a custom role
     */
    @SuppressWarnings("java:S107") // Entity constructor requires all persistent fields
    public User(UUID tenantId, TenantRole tenantRole, Role legacyRole, String email, String passwordHash,
                String firstName, String lastName, UserStatus status) {
        this.tenantId = tenantId;
        this.tenantRole = tenantRole;
        this.role = legacyRole;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.status = status;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    /** The transitional fixed role, or {@code null} for a member of a custom role. */
    public Role getRole() {
        return role;
    }

    /** The company role this member holds, or {@code null} for a platform operator. */
    public TenantRole getTenantRole() {
        return tenantRole;
    }

    /**
     * The role name the API has always exposed as {@code role}: the fixed role
     * when there is one, {@value #CUSTOM_ROLE} for a member of a custom role.
     * Clients should move to the role id and permission list.
     */
    public String legacyRoleName() {
        return role != null ? role.getName().name() : CUSTOM_ROLE;
    }

    /**
     * Moves the member to another company role, keeping the transitional
     * fixed-role mirror in step with it.
     *
     * @param legacyRole the fixed role mirroring {@code newRole}, or
     *                   {@code null} when it is a custom role
     */
    public void assignRole(TenantRole newRole, Role legacyRole) {
        this.tenantRole = newRole;
        this.role = legacyRole;
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

    /** True while a lock is in force; a lapsed lock is not a lock. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /**
     * Records a failed login, engaging the lock once the threshold is reached.
     *
     * @return true if this failure locked the account
     */
    public boolean registerFailedLogin(Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= LockoutPolicy.MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LockoutPolicy.LOCK_DURATION);
            failedLoginCount = 0; // start a fresh count for the next window
            return true;
        }
        return false;
    }

    /** Clears the failure count and any lapsed lock after a successful login. */
    public void registerSuccessfulLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
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
