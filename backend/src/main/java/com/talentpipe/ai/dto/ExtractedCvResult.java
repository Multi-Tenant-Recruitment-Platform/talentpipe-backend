package com.talentpipe.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Plain-text CV extraction result returned by Python {@code POST /api/v1/extract}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExtractedCvResult(
        String extractedText,
        int charCount,
        int pageCount,
        String format,
        String extractor,
        boolean emptyOrScanned
) {
}
