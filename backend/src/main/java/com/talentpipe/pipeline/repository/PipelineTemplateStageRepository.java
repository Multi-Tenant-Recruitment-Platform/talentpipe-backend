package com.talentpipe.pipeline.repository;

import com.talentpipe.pipeline.entity.PipelineTemplateStage;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link PipelineTemplateStage}.
 */
@Repository
public interface PipelineTemplateStageRepository extends JpaRepository<PipelineTemplateStage, UUID> {

    List<PipelineTemplateStage> findByTemplateIdOrderByPositionAsc(UUID templateId);

    void deleteByTemplateId(UUID templateId);
}
