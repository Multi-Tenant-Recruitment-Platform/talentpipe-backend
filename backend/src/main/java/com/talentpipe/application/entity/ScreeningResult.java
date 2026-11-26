package com.talentpipe.application.entity;

import java.util.List;

/**
 * A finished screening, as {@link ApplicationScore} stores it. The entity takes
 * this rather than the AI service's response so that it does not depend on how
 * that service words things.
 *
 * @param overallScore    0 to 100
 * @param summary         one paragraph for the recruiter
 * @param strengths       what speaks for the candidate
 * @param gaps            what is missing
 * @param criterionScores the score on each criterion
 * @param modelVersion    the model that produced it
 * @param promptVersion   the prompt that produced it
 * @param cached          whether it was reused from an identical earlier screening
 */
public record ScreeningResult(
        int overallScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<CriterionScore> criterionScores,
        String modelVersion,
        String promptVersion,
        boolean cached) {

    public ScreeningResult {
        strengths = copy(strengths);
        gaps = copy(gaps);
        criterionScores = copy(criterionScores);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
