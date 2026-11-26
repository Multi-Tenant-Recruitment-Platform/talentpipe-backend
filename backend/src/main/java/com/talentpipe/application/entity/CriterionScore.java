package com.talentpipe.application.entity;

/**
 * How an application did on one criterion. Stored as JSON inside
 * {@link ApplicationScore}.
 *
 * @param name      the criterion
 * @param score     0 to 100
 * @param rationale why, with evidence from the CV
 */
public record CriterionScore(String name, int score, String rationale) {
}
