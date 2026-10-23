package com.talentpipe.pipeline.dto;

import com.talentpipe.pipeline.entity.StageType;
import java.util.UUID;

/**
 * Representation of an individual pipeline stage.
 */
public record PipelineStageResponse(
        UUID id,
        int position,
        StageType stageType,
        String name,
        Integer benchmark
) {
}
