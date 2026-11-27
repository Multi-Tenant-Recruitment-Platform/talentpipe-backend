package com.talentpipe.job.entity;

/**
 * What a weighted matching criterion is about (V27).
 *
 * <p>Since V28 the 100% is shared by skills alone. Tools and technologies,
 * relevant experience, education and certifications are deliberately absent:
 * they are never weighted, only passed to screening as context from the
 * vacancy's candidate requirements. The category stays on the wire and in the
 * table so another weighted kind can be added without changing either shape.</p>
 */
public enum MatchingCategory {

    /** One skill, e.g. Java — a required skill as a must-have, a preferred skill as preferred. */
    TECHNICAL_SKILL
}
