package com.talentpipe.notification.event;

import com.talentpipe.notification.entity.NotificationType;
import java.util.UUID;

/**
 * Domain event: some business action needs an email sent.
 *
 * <p>Publishing this is the ONLY way business code asks for mail. The
 * publisher's transaction commits first; delivery then happens asynchronously
 * in {@code NotificationDispatcher}, so a mail outage can never fail — or slow
 * down — registration, password reset or invitation.</p>
 *
 * <p>The two factories encode the identity split the platform is built on:</p>
 * <ul>
 *   <li>{@link #forUser} — a company user. Tenant-scoped and backed by a
 *       {@code users} row, so the attempt is recorded in {@code notifications}.</li>
 *   <li>{@link #forCandidate} — a candidate. No tenant, not a {@code users}
 *       row, so it is sent but NOT recorded in that tenant-scoped table.</li>
 * </ul>
 *
 * @param type       what is being sent
 * @param tenantId   owning tenant, or {@code null} for candidates / SUPER_ADMIN
 * @param userId     {@code users} row id, or {@code null} for candidates
 * @param recipient  destination address
 * @param link       the action URL (carries a single-use token — never logged)
 * @param recipientName display name used in the greeting
 */
public record NotificationRequestedEvent(
        NotificationType type,
        UUID tenantId,
        UUID userId,
        String recipient,
        String link,
        String recipientName) {

    /** Mail to a company user — persisted in the tenant-scoped notifications table. */
    public static NotificationRequestedEvent forUser(NotificationType type, UUID tenantId, UUID userId,
                                                     String recipient, String link, String recipientName) {
        return new NotificationRequestedEvent(type, tenantId, userId, recipient, link, recipientName);
    }

    /** Mail to a candidate — delivered, but not recorded in the tenant-scoped table. */
    public static NotificationRequestedEvent forCandidate(NotificationType type, String recipient,
                                                          String link, String recipientName) {
        return new NotificationRequestedEvent(type, null, null, recipient, link, recipientName);
    }

    /**
     * Whether this attempt can be written to {@code notifications}, whose
     * schema requires both a tenant and a users row (SUPER_ADMIN mail, having
     * no tenant, is therefore also unrecorded).
     */
    public boolean isRecordable() {
        return tenantId != null && userId != null;
    }
}
