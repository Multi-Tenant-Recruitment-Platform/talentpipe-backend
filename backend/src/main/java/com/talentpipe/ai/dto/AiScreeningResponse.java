package com.talentpipe.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Screening result returned by Python {@code POST /api/v1/screen}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiScreeningResponse(
        int overallScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<CriterionScore> criterionScores,
        String modelVersion,
        String promptVersion,
        boolean cached
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CriterionScore(String name, int score, String rationale) {
    }
}
