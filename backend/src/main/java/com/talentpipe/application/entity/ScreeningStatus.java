package com.talentpipe.application.entity;

/** The outcome of an application's latest screening (V26). */
public enum ScreeningStatus {

    /** Being screened. */
    PENDING,

    /** Screened: the row holds a score. */
    SCORED,

    /** Could not be screened; a person has to look at it. */
    SCORING_FAILED
}
