package com.talentpipe.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * A page of a vacancy's applicants, with what the dashboard shows around it.
 *
 * @param counts        per group, independent of the filter and search
 * @param thresholds    what scores are judged against for this vacancy
 * @param blindMode     whether names and emails are hidden (and search is off)
 * @param lastChangedAt when anything about these applicants last changed; the
 *                      dashboard polls the summary and reloads when this moves
 */
public record ApplicantPage(
        List<ApplicantRow> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        ApplicantCounts counts,
        Thresholds thresholds,
        boolean blindMode,
        Instant lastChangedAt) {

    /**
     * @param fromPipeline false when the vacancy has no pipeline of its own and
     *                     the defaults (80 / 70) are used
     */
    public record Thresholds(int auto, int manual, boolean fromPipeline) {
    }
}
