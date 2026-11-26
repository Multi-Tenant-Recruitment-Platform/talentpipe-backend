package com.talentpipe.ai.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Plain-text CV extraction wire response returned by Python {@code POST /api/v1/extract}.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CvExtractionResponse(
        String extractedText,
        int charCount,
        int pageCount,
        String format,
        String extractor,
        boolean emptyOrScanned
) {

    /** Leaves {@code extractedText} out so candidate PII never enters logs. */
    @Override
    public String toString() {
        return "CvExtractionResponse[charCount=" + charCount
                + ", pageCount=" + pageCount
                + ", format=" + format
                + ", extractor=" + extractor
                + ", emptyOrScanned=" + emptyOrScanned + "]";
    }
}
