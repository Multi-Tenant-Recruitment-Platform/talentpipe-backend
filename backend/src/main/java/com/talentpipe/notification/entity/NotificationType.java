package com.talentpipe.notification.entity;

/** Kind of outbound notification — mirrors the CHECK constraint on notifications.type. */
public enum NotificationType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET,
    INVITATION
}
