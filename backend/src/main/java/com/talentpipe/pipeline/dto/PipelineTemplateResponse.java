package com.talentpipe.pipeline.dto;

import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full details of a pipeline template, including its stages in order.
 */
public record PipelineTemplateResponse(
        UUID id,
        String name,
        String description,
        int defaultAutoThreshold,
        int defaultManualThreshold,
        boolean idealMatchingEnabled,
        PipelineTemplateStatus status,
        int stageCount,
        int interviewRoundCount,
        List<PipelineStageResponse> stages,
        Instant createdAt,
        Instant updatedAt,
        Instant archivedAt
) {
}
