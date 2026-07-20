package com.talentpipe.candidate.entity;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.common.entity.BaseEntity;
import com.talentpipe.common.util.LockoutPolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A tenant-independent candidate profile and applicant account.
 *
 * <p>Candidates authenticate globally, meaning they do not belong to any
 * particular company tenant. Under the modular monolith architecture guidelines,
 * they are kept completely separate from the auth module's {@code users} table.</p>
 */
@Entity
@Table(name = "candidates")
public class Candidate extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 200)
    private String fullName;

    @Column(name = "identity_card_number", nullable = false, length = 30)
    private String identityCardNumber;

    @Column(name = "address", nullable = false, length = 500)
    private String address;

    @Column(name = "contact_number", nullable = false, length = 20)
    private String contactNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFICATION;

    /** Consecutive failed logins; reset to zero on success (brute-force protection). */
    @Column(name = "failed_login_count", nullable = false)
    private short failedLoginCount = 0;

    /** Set when the failure threshold is hit; logins are refused until it passes. */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    protected Candidate() {
        // for JPA
    }

    public Candidate(String email, String passwordHash, String fullName,
                     String identityCardNumber, String address, String contactNumber,
                     UserStatus status) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.identityCardNumber = identityCardNumber;
        this.address = address;
        this.contactNumber = contactNumber;
        this.status = status;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getIdentityCardNumber() {
        return identityCardNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getContactNumber() {
        return contactNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
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

    /** Deliberately excludes email and passwordHash — safe for logs. */
    @Override
    public String toString() {
        return "Candidate{id=" + getId() + ", status=" + status + "}";
    }
}
