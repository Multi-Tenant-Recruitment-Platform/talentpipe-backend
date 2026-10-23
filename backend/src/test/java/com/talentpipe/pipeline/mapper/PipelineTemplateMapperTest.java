package com.talentpipe.pipeline.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.talentpipe.pipeline.PipelineTemplateFixtures;
import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStage;
import com.talentpipe.pipeline.entity.StageType;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PipelineTemplateMapperTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private PipelineTemplateMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new PipelineTemplateMapper();
    }

    @Test
    void toResponse_mapsAllFieldsCorrectly() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);

        PipelineTemplateResponse response = mapper.toResponse(entity);

        assertEquals(entity.getName(), response.name());
        assertEquals(entity.getDescription(), response.description());
        assertEquals(entity.getDefaultAutoThreshold(), response.defaultAutoThreshold());
        assertEquals(entity.getDefaultManualThreshold(), response.defaultManualThreshold());
        assertTrue(response.idealMatchingEnabled());
        assertEquals(4, response.stageCount());
        assertEquals(1, response.interviewRoundCount());
        assertEquals(4, response.stages().size());
        assertEquals(StageType.POST_APPLICATION, response.stages().get(0).stageType());
        assertEquals(StageType.SCREENING, response.stages().get(1).stageType());
        assertEquals(StageType.INTERVIEW, response.stages().get(2).stageType());
        assertEquals(75, response.stages().get(2).benchmark());
        assertEquals(StageType.ONBOARD, response.stages().get(3).stageType());
    }

    @Test
    void toSummaryResponse_mapsSummaryFieldsCorrectly() {
        PipelineTemplate entity = PipelineTemplateFixtures.createEntity(TENANT_ID);

        PipelineTemplateSummaryResponse summary = mapper.toSummaryResponse(entity);

        assertEquals(entity.getName(), summary.name());
        assertEquals(entity.getDescription(), summary.description());
        assertEquals(4, summary.stageCount());
        assertEquals(1, summary.interviewRoundCount());
        assertNotNull(summary.status());
    }

    @Test
    void toStageResponse_mapsStageFieldsCorrectly() {
        PipelineTemplateStage stage = new PipelineTemplateStage(
                TENANT_ID, 2, StageType.INTERVIEW, "Round 2", 80);

        PipelineStageResponse response = mapper.toStageResponse(stage);

        assertEquals(2, response.position());
        assertEquals(StageType.INTERVIEW, response.stageType());
        assertEquals("Round 2", response.name());
        assertEquals(80, response.benchmark());
    }

    @Test
    void toStageEntity_createsStageEntityCorrectly() {
        PipelineStageRequest request = new PipelineStageRequest(
                1, StageType.SCREENING, "Initial Screening", null);

        PipelineTemplateStage stage = mapper.toStageEntity(TENANT_ID, request, "Initial Screening");

        assertEquals(TENANT_ID, stage.getTenantId());
        assertEquals(1, stage.getPosition());
        assertEquals(StageType.SCREENING, stage.getStageType());
        assertEquals("Initial Screening", stage.getName());
        assertNull(stage.getBenchmark());
    }
}
