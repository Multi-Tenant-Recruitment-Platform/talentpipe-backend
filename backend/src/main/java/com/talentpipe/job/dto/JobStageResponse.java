package com.talentpipe.job.dto;

import com.talentpipe.pipeline.entity.StageType;
import java.util.UUID;

/**
 * One stage of the pipeline a vacancy runs on, as copied from its template
 * when the vacancy was created.
 *
 * @param benchmark the pass mark of an interview round; {@code null} for every other stage
 */
public record JobStageResponse(
        UUID id,
        int position,
        StageType stageType,
        String name,
        Integer benchmark
) {
}
