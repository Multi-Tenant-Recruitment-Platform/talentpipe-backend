package com.talentpipe.job.service;

import com.talentpipe.pipeline.dto.PipelineStageResponse;
import java.util.List;
import java.util.UUID;

/**
 * What a new vacancy takes from its template: the screening settings with any
 * overrides applied, and the stages to copy. A value read from the template at
 * one moment; later changes to the template do not reach it.
 */
public record PipelineSnapshot(UUID templateId, int autoThreshold, int manualThreshold, boolean blindModeEnabled,
                               List<PipelineStageResponse> stages) {

    public PipelineSnapshot {
        stages = List.copyOf(stages);
    }
}
