package com.talentpipe.candidate.entity;

import com.talentpipe.auth.entity.UserStatus;
import com.talentpipe.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

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

    @Override
    public String toString() {
        return "Candidate{id=" + getId() + ", email='" + email + "', status=" + status + "}";
    }
}
