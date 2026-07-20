package com.talentpipe.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Audit record of one outbound email to a COMPANY USER.
 *
 * <p>Tenant-scoped and tied to a {@code users} row, so it covers verification,
 * password reset and invitation mail for company staff. Candidate email is
 * deliberately NOT recorded here: candidates have no tenant and no users row,
 * and widening this table to hold them would break its tenant scoping. See
 * docs/DECISIONS.md (ADR-5).</p>
 *
 * <p>Lifecycle: created PENDING before the send is attempted, then moved to
 * SENT ({@code sentAt} set) or FAILED ({@code errorDetail} set). The row is
 * the reason a failed send is recoverable — operations can find FAILED
 * attempts, and the user can trigger a resend.</p>
 */
@Entity
@Table(name = "notifications")
public class Notification {

    /** Failure text is truncated to the column width before persisting. */
    private static final int MAX_ERROR_DETAIL = 500;

    @Id
    @UuidGenerator
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false, length = 30)
    private NotificationType type;

    @Column(name = "recipient", nullable = false, updatable = false, length = 255)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "error_detail", length = MAX_ERROR_DETAIL)
    private String errorDetail;

    @Column(name = "sent_at")
    private Instant sentAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
        // for JPA
    }

    public Notification(UUID tenantId, UUID userId, NotificationType type, String recipient) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.type = type;
        this.recipient = recipient;
        this.status = NotificationStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getRecipient() {
        return recipient;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    /** Marks the attempt delivered. */
    public void markSent(Instant now) {
        this.status = NotificationStatus.SENT;
        this.sentAt = now;
        this.errorDetail = null;
    }

    /** Marks the attempt failed, keeping a truncated reason for operations. */
    public void markFailed(String reason) {
        this.status = NotificationStatus.FAILED;
        this.errorDetail = reason == null ? null
                : reason.substring(0, Math.min(reason.length(), MAX_ERROR_DETAIL));
    }

    /** Excludes the recipient address — no PII in logs. */
    @Override
    public String toString() {
        return "Notification{id=" + id + ", type=" + type + ", status=" + status + "}";
    }
}
