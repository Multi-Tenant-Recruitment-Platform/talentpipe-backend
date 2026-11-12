package com.talentpipe.job.service;

import com.talentpipe.job.entity.VacancyPipeline;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.entity.StageType;
import java.util.List;
import java.util.UUID;

/**
 * What a new vacancy takes from its template: the template's name, the
 * screening settings with any overrides applied, and the stages to copy. A
 * value read from the template at one moment; later changes to the template
 * do not reach it.
 */
public record PipelineSnapshot(UUID templateId, String templateName, int autoThreshold, int manualThreshold,
                               boolean blindModeEnabled, List<PipelineStageResponse> stages) {

    public PipelineSnapshot {
        stages = List.copyOf(stages);
    }

    /** How many stages the vacancy will run on. */
    public int stageCount() {
        return stages.size();
    }

    /** How many of those stages are interview rounds. */
    public int interviewRoundCount() {
        return (int) stages.stream().filter(stage -> stage.stageType() == StageType.INTERVIEW).count();
    }

    /** What the vacancy itself stores of this snapshot; the stages are copied separately. */
    public VacancyPipeline toVacancyPipeline() {
        return new VacancyPipeline(templateId, templateName, autoThreshold, manualThreshold, blindModeEnabled,
                stageCount(), interviewRoundCount());
    }
}
