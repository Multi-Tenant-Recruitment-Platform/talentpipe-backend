package com.talentpipe.pipeline;

import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStage;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.entity.StageType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Shared fixtures for pipeline template tests.
 */
public final class PipelineTemplateFixtures {

    private PipelineTemplateFixtures() {
    }

    public static List<PipelineStageRequest> standardStages() {
        return List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.SCREENING, "Screening", null),
                new PipelineStageRequest(2, StageType.INTERVIEW, "Technical Round", 75),
                new PipelineStageRequest(3, StageType.ONBOARD, "Onboard", null)
        );
    }

    public static PipelineTemplateRequest validRequest() {
        return new PipelineTemplateRequest(
                "Standard Engineering Pipeline",
                "Default hiring workflow for engineers",
                80,
                70,
                true,
                standardStages()
        );
    }

    public static PipelineTemplate createEntity(UUID tenantId) {
        PipelineTemplate template = new PipelineTemplate(
                tenantId,
                "Standard Engineering Pipeline",
                "Default hiring workflow for engineers",
                80,
                70,
                true,
                UUID.randomUUID()
        );
        template.addStage(new PipelineTemplateStage(tenantId, 0, StageType.POST_APPLICATION, "Post Application", null));
        template.addStage(new PipelineTemplateStage(tenantId, 1, StageType.SCREENING, "Screening", null));
        template.addStage(new PipelineTemplateStage(tenantId, 2, StageType.INTERVIEW, "Technical Round", 75));
        template.addStage(new PipelineTemplateStage(tenantId, 3, StageType.ONBOARD, "Onboard", null));
        return template;
    }

    public static PipelineTemplateResponse sampleResponse(UUID id) {
        return new PipelineTemplateResponse(
                id,
                "Standard Engineering Pipeline",
                "Default hiring workflow for engineers",
                80,
                70,
                true,
                PipelineTemplateStatus.ACTIVE,
                4,
                1,
                List.of(
                        new PipelineStageResponse(UUID.randomUUID(), 0, StageType.POST_APPLICATION, "Post Application", null),
                        new PipelineStageResponse(UUID.randomUUID(), 1, StageType.SCREENING, "Screening", null),
                        new PipelineStageResponse(UUID.randomUUID(), 2, StageType.INTERVIEW, "Technical Round", 75),
                        new PipelineStageResponse(UUID.randomUUID(), 3, StageType.ONBOARD, "Onboard", null)
                ),
                Instant.now(),
                Instant.now(),
                null
        );
    }

    public static PipelineTemplateSummaryResponse sampleSummaryResponse(UUID id) {
        return new PipelineTemplateSummaryResponse(
                id,
                "Standard Engineering Pipeline",
                "Default hiring workflow for engineers",
                80,
                70,
                true,
                PipelineTemplateStatus.ACTIVE,
                4,
                1,
                Instant.now(),
                Instant.now(),
                null
        );
    }
}
