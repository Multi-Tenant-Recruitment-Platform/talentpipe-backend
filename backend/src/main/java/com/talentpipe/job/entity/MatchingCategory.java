package com.talentpipe.job.entity;

/**
 * What a weighted matching criterion is about (V27). Every kind shares the
 * same 100%. Education and certifications are deliberately absent: they are
 * never weighted, only passed to screening as context.
 */
public enum MatchingCategory {

    /** One technical skill, e.g. Java. */
    TECHNICAL_SKILL,

    /** One tool, technology or platform, e.g. Docker. */
    TOOL,

    /** Relevant experience, judged against the vacancy's minimum years. At most one per vacancy. */
    EXPERIENCE;

    /** Whether criteria of this kind say if they are a must-have or preferred. */
    public boolean hasRequirement() {
        return this != EXPERIENCE;
    }
}
