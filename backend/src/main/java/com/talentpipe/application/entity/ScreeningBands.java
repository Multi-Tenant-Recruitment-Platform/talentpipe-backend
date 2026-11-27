package com.talentpipe.application.entity;

import com.talentpipe.job.entity.JobVacancy;

/**
 * The two thresholds a score is judged against, and where they came from.
 *
 * <p>A vacancy on a pipeline carries its own (copied from its template). One
 * filed before vacancies ran on pipelines has none, and is judged against the
 * same defaults a new pipeline template starts with, so its applicants can
 * still be ranked. {@link #fromPipeline} says which, so the screen can tell
 * the recruiter.</p>
 *
 * @param auto         at or above: auto-advanced
 * @param manual       at or above, below {@code auto}: manual review; below: rejected
 * @param fromPipeline whether the vacancy's own thresholds were used
 */
public record ScreeningBands(int auto, int manual, boolean fromPipeline) {

    /** The defaults of a new pipeline template ({@code PipelineTemplate}). */
    public static final int DEFAULT_AUTO = 80;
    public static final int DEFAULT_MANUAL = 70;

    public ScreeningBands {
        if (auto < 0 || auto > 100 || manual < 0 || manual > 100 || manual >= auto) {
            throw new IllegalArgumentException("Thresholds must satisfy 0 <= manual < auto <= 100");
        }
    }

    public static ScreeningBands of(JobVacancy vacancy) {
        Integer auto = vacancy.getAutoThreshold();
        Integer manual = vacancy.getManualThreshold();
        if (auto != null && manual != null) {
            return new ScreeningBands(auto, manual, true);
        }
        return new ScreeningBands(DEFAULT_AUTO, DEFAULT_MANUAL, false);
    }

    /** The band {@code score} falls in. Exactly a threshold counts as reaching it. */
    public ScreeningOutcome outcomeOf(int score) {
        if (score >= auto) {
            return ScreeningOutcome.AUTO_ADVANCED;
        }
        if (score >= manual) {
            return ScreeningOutcome.MANUAL_REVIEW;
        }
        return ScreeningOutcome.AUTO_REJECTED;
    }
}
