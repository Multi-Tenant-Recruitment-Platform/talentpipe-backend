package com.talentpipe.pipeline.dto;

import com.talentpipe.pipeline.entity.StageType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload representing a single pipeline stage.
 */
public record PipelineStageRequest(
        @NotNull(message = "Position is required")
        @Min(value = 0, message = "Position must be greater than or equal to 0")
        Integer position,

        @NotNull(message = "Stage type is required")
        StageType stageType,

        @NotBlank(message = "Stage name is required")
        @Size(max = 100, message = "Stage name cannot exceed 100 characters")
        String name,

        @Min(value = 0, message = "Benchmark must be at least 0")
        @Max(value = 100, message = "Benchmark cannot exceed 100")
        Integer benchmark
) {
}
