package com.talentpipe.job.entity;

/**
 * How firmly a skill or tool is asked for (V27). Passed to screening so it can
 * call out a missing must-have; the weight alone decides how much it scores.
 */
public enum MatchingRequirement {
    MUST_HAVE,
    PREFERRED
}
