package com.talentpipe.job.dto;

/**
 * Turns a vacancy's features on or off ({@code PATCH /jobs/{id}/features},
 * PB-015). A switch left out (or {@code null}) keeps its value.
 *
 * @param aiScreeningEnabled       score incoming applications against the vacancy
 * @param candidateMatchingEnabled suggest people from the talent pool who fit it
 */
public record VacancyFeaturesRequest(Boolean aiScreeningEnabled, Boolean candidateMatchingEnabled) {
}
