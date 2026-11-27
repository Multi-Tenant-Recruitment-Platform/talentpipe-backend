package com.talentpipe.application.entity;

/**
 * Where an application stands in the recruiter's review (V29) — what the
 * applicant dashboard filters, counts and ranks on.
 *
 * <p>Set from the screening outcome when an application is screened, and from
 * then on by recruiters. A recruiter's decision is never overwritten by a
 * later screening.</p>
 */
public enum ReviewStatus {

    /** Not screened yet. */
    PENDING_SCREENING,

    /** The score reached the vacancy's auto-advance threshold. */
    AUTO_ADVANCED,

    /** Waiting for a person: the score fell between the thresholds, or it could not be screened. */
    MANUAL_REVIEW,

    /** A recruiter moved the applicant on (shortlisted, or advanced from manual review). */
    ADVANCED_BY_RECRUITER,

    /** Below the manual-review threshold, or rejected by a recruiter. */
    REJECTED,

    /** The candidate withdrew. */
    WITHDRAWN;

    /** Whether a person has already decided, so screening must leave it alone. */
    public boolean isDecided() {
        return this == ADVANCED_BY_RECRUITER || this == REJECTED || this == WITHDRAWN;
    }
}
