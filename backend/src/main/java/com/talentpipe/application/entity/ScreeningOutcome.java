package com.talentpipe.application.entity;

/** The verdict on one screening score (V29): which band of the vacancy's thresholds it fell in. */
public enum ScreeningOutcome {

    /** At or above the auto-advance threshold. */
    AUTO_ADVANCED,

    /** At or above the manual-review threshold, below auto-advance. */
    MANUAL_REVIEW,

    /** Below the manual-review threshold. */
    AUTO_REJECTED;

    /** The review status an application takes when this is its first verdict. */
    public ReviewStatus reviewStatus() {
        return switch (this) {
            case AUTO_ADVANCED -> ReviewStatus.AUTO_ADVANCED;
            case MANUAL_REVIEW -> ReviewStatus.MANUAL_REVIEW;
            case AUTO_REJECTED -> ReviewStatus.REJECTED;
        };
    }
}
