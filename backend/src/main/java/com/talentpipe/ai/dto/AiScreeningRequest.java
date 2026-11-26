package com.talentpipe.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

/**
 * Request payload sent to Python {@code POST /api/v1/screen}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AiScreeningRequest(
        String cvText,
        String jobDescription,
        List<CriterionWeight> criteriaWeights,
        boolean blindMode,
        boolean bypassCache
) {

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CriterionWeight(String name, double weight) {
    }
}
