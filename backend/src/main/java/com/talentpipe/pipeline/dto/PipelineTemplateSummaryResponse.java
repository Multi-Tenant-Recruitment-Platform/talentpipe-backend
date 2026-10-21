package com.talentpipe.pipeline.dto;

import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Summary representation of a pipeline template for list views.
 */
public record PipelineTemplateSummaryResponse(
        UUID id,
        String name,
        String description,
        int defaultAutoThreshold,
        int defaultManualThreshold,
        boolean idealMatchingEnabled,
        PipelineTemplateStatus status,
        int stageCount,
        int interviewRoundCount,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
) {
}
