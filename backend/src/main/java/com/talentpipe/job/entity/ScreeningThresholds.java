package com.talentpipe.job.entity;

import com.talentpipe.common.exception.BusinessRuleException;

/**
 * The rule a vacancy's screening thresholds always obey, wherever they are
 * set: when a vacancy is created from a template and when an edit changes
 * them. AI scores at or above the auto threshold advance on their own, scores
 * from the manual threshold up wait for a person, and lower scores are
 * rejected — so the manual threshold has to sit below the auto one.
 *
 * <p>Requests reach this already range-checked ({@code @Min}/{@code @Max} on
 * {@code JobVacancyRequest} answer 400 first); the range check here guards any
 * other caller, and the database's CHECK constraints (V23) are the last line.</p>
 */
public final class ScreeningThresholds {

    public static final String OUT_OF_RANGE = "Set the thresholds between 0 and 100.";
    public static final String OUT_OF_ORDER = "The manual threshold must be lower than the auto threshold.";

    private static final int MIN = 0;
    private static final int MAX = 100;

    private ScreeningThresholds() {
        // static rule only
    }

    /**
     * @throws BusinessRuleException (422) a threshold outside 0–100, or the
     *         manual threshold not below the auto threshold
     */
    public static void requireValid(int auto, int manual) {
        if (auto < MIN || auto > MAX || manual < MIN || manual > MAX) {
            throw new BusinessRuleException(OUT_OF_RANGE);
        }
        if (manual >= auto) {
            throw new BusinessRuleException(OUT_OF_ORDER);
        }
    }
}
