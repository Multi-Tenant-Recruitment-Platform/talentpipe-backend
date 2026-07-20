package com.talentpipe.notification.entity;

/** Delivery state of a notification attempt. */
public enum NotificationStatus {
    /** Recorded, delivery not yet attempted. */
    PENDING,
    /** Accepted by the transport ({@code sent_at} set). */
    SENT,
    /** Transport rejected it or errored ({@code error_detail} set). */
    FAILED
}
