package com.talentpipe.pipeline.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Request payload to create or replace a pipeline template and its stages.
 */
public record PipelineTemplateRequest(
        @NotBlank(message = "Template name is required")
        @Size(max = 120, message = "Template name cannot exceed 120 characters")
        String name,

        @Size(max = 1000, message = "Description cannot exceed 1000 characters")
        String description,

        @NotNull(message = "Default auto threshold is required")
        @Min(value = 0, message = "Default auto threshold must be at least 0")
        @Max(value = 100, message = "Default auto threshold cannot exceed 100")
        Integer defaultAutoThreshold,

        @NotNull(message = "Default manual threshold is required")
        @Min(value = 0, message = "Default manual threshold must be at least 0")
        @Max(value = 100, message = "Default manual threshold cannot exceed 100")
        Integer defaultManualThreshold,

        Boolean idealMatchingEnabled,

        @NotEmpty(message = "Stages are required")
        @Valid
        List<PipelineStageRequest> stages
) {
    public PipelineTemplateRequest {
        if (idealMatchingEnabled == null) {
            idealMatchingEnabled = false;
        }
    }
}
