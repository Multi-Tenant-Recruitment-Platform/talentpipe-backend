package com.talentpipe.application.entity;

/** What a line of an application's history records (V29). */
public enum StageEventType {
    SUBMITTED,
    AI_SCREENED,
    SCREENING_FAILED,
    /** The vacancy does not screen with AI, so the applicant went straight to a person. */
    SCREENING_SKIPPED,
    ADVANCED,
    SHORTLISTED,
    STAGE_CHANGED,
    /** A status change through the older {@code PATCH /applications/{id}/status}. */
    STATUS_CHANGED,
    REJECTED,
    HIRED,
    WITHDRAWN
}
