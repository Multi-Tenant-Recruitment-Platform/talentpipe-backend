package com.talentpipe.pipeline.mapper;

import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStage;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Mapper for translating between PipelineTemplate domain entities and API DTOs.
 */
@Component
public class PipelineTemplateMapper {

    public PipelineTemplateResponse toResponse(PipelineTemplate template) {
        List<PipelineStageResponse> stageResponses = template.getStages().stream()
                .sorted(Comparator.comparingInt(PipelineTemplateStage::getPosition))
                .map(this::toStageResponse)
                .toList();

        return new PipelineTemplateResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getDefaultAutoThreshold(),
                template.getDefaultManualThreshold(),
                template.isIdealMatchingEnabled(),
                template.getStatus(),
                template.getStages().size(),
                template.getInterviewRoundCount(),
                stageResponses,
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getArchivedAt()
        );
    }

    public PipelineTemplateSummaryResponse toSummaryResponse(PipelineTemplate template) {
        return new PipelineTemplateSummaryResponse(
                template.getId(),
                template.getName(),
                template.getDescription(),
                template.getDefaultAutoThreshold(),
                template.getDefaultManualThreshold(),
                template.isIdealMatchingEnabled(),
                template.getStatus(),
                template.getStages().size(),
                template.getInterviewRoundCount(),
                template.getCreatedAt(),
                template.getUpdatedAt(),
                template.getArchivedAt()
        );
    }

    public PipelineStageResponse toStageResponse(PipelineTemplateStage stage) {
        return new PipelineStageResponse(
                stage.getId(),
                stage.getPosition(),
                stage.getStageType(),
                stage.getName(),
                stage.getBenchmark()
        );
    }

    public PipelineTemplateStage toStageEntity(UUID tenantId, PipelineStageRequest request, String sanitizedName) {
        return new PipelineTemplateStage(
                tenantId,
                request.position(),
                request.stageType(),
                sanitizedName,
                request.benchmark()
        );
    }
}
