package com.talentpipe.pipeline.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PipelineTemplateEntityTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    @Test
    void entity_creationAndDefaults_workCorrectly() {
        PipelineTemplate template = new PipelineTemplate(
                TENANT_ID, "Engineering", "Desc", 80, 70, true, USER_ID);

        assertEquals(TENANT_ID, template.getTenantId());
        assertEquals("Engineering", template.getName());
        assertEquals("Desc", template.getDescription());
        assertEquals(80, template.getDefaultAutoThreshold());
        assertEquals(70, template.getDefaultManualThreshold());
        assertTrue(template.isIdealMatchingEnabled());
        assertEquals(USER_ID, template.getCreatedBy());
        assertEquals(PipelineTemplateStatus.ACTIVE, template.getStatus());
        assertTrue(template.isActive());
        assertFalse(template.isArchived());
        assertNull(template.getArchivedAt());
        assertEquals(0, template.getVersion());
    }

    @Test
    void addStage_and_clearStages_manageBidirectionalRelationship() {
        PipelineTemplate template = new PipelineTemplate(
                TENANT_ID, "Sales", "Desc", 85, 65, false, USER_ID);
        PipelineTemplateStage stage1 = new PipelineTemplateStage(
                TENANT_ID, 0, StageType.POST_APPLICATION, "Apply", null);
        PipelineTemplateStage stage2 = new PipelineTemplateStage(
                TENANT_ID, 1, StageType.INTERVIEW, "Interview", 70);

        template.addStage(stage1);
        template.addStage(stage2);

        assertEquals(2, template.getStages().size());
        assertEquals(template, stage1.getTemplate());
        assertEquals(template, stage2.getTemplate());
        assertEquals(1, template.getInterviewRoundCount());

        template.clearStages();
        assertTrue(template.getStages().isEmpty());
        assertNull(stage1.getTemplate());
    }

    @Test
    void setStages_replacesExistingStages() {
        PipelineTemplate template = new PipelineTemplate(
                TENANT_ID, "Marketing", "Desc", 80, 70, false, USER_ID);
        PipelineTemplateStage oldStage = new PipelineTemplateStage(
                TENANT_ID, 0, StageType.POST_APPLICATION, "Old", null);
        template.addStage(oldStage);

        PipelineTemplateStage newStage = new PipelineTemplateStage(
                TENANT_ID, 0, StageType.POST_APPLICATION, "New", null);
        template.setStages(List.of(newStage));

        assertEquals(1, template.getStages().size());
        assertEquals("New", template.getStages().get(0).getName());
        assertNull(oldStage.getTemplate());
    }

    @Test
    void archive_setsStatusAndArchivedTimestamp() {
        PipelineTemplate template = new PipelineTemplate(
                TENANT_ID, "Support", "Desc", 80, 70, false, USER_ID);

        template.archive();

        assertTrue(template.isArchived());
        assertFalse(template.isActive());
        assertEquals(PipelineTemplateStatus.ARCHIVED, template.getStatus());
        assertNotNull(template.getArchivedAt());
    }

    @Test
    void settersAndGetters_workAsExpected() {
        PipelineTemplate template = new PipelineTemplate();
        template.setTenantId(TENANT_ID);
        template.setName("Ops");
        template.setDescription("Ops pipeline");
        template.setDefaultAutoThreshold(90);
        template.setDefaultManualThreshold(80);
        template.setIdealMatchingEnabled(true);
        template.setCreatedBy(USER_ID);
        template.setArchivedAt(null);

        assertEquals(TENANT_ID, template.getTenantId());
        assertEquals("Ops", template.getName());
        assertEquals("Ops pipeline", template.getDescription());
        assertEquals(90, template.getDefaultAutoThreshold());
        assertEquals(80, template.getDefaultManualThreshold());
        assertTrue(template.isIdealMatchingEnabled());
        assertEquals(USER_ID, template.getCreatedBy());

        PipelineTemplateStage stage = new PipelineTemplateStage(
                TENANT_ID, 1, StageType.SCREENING, "Initial", null);
        stage.setPosition(2);
        stage.setStageType(StageType.INTERVIEW);
        stage.setName("Technical");
        stage.setBenchmark(85);
        stage.setTenantId(TENANT_ID);

        assertEquals(2, stage.getPosition());
        assertEquals(StageType.INTERVIEW, stage.getStageType());
        assertEquals("Technical", stage.getName());
        assertEquals(85, stage.getBenchmark());
        assertEquals(TENANT_ID, stage.getTenantId());
    }
}
