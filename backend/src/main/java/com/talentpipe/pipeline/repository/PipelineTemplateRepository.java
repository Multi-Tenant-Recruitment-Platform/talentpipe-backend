package com.talentpipe.pipeline.repository;

import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

/**
 * Persistence operations for {@link PipelineTemplate}.
 */
@Repository
public interface PipelineTemplateRepository extends JpaRepository<PipelineTemplate, UUID>,
        JpaSpecificationExecutor<PipelineTemplate> {

    @EntityGraph(attributePaths = {"stages"})
    Optional<PipelineTemplate> findWithStagesByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PipelineTemplate> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PipelineTemplate> findByTenantId(UUID tenantId, Pageable pageable);

    Page<PipelineTemplate> findByTenantIdAndStatus(UUID tenantId, PipelineTemplateStatus status, Pageable pageable);

    Page<PipelineTemplate> findByTenantIdAndNameContainingIgnoreCase(UUID tenantId, String name, Pageable pageable);

    Page<PipelineTemplate> findByTenantIdAndStatusAndNameContainingIgnoreCase(
            UUID tenantId, PipelineTemplateStatus status, String name, Pageable pageable);

    boolean existsByTenantIdAndNameIgnoreCaseAndStatus(UUID tenantId, String name, PipelineTemplateStatus status);

    boolean existsByTenantIdAndNameIgnoreCaseAndStatusAndIdNot(
            UUID tenantId, String name, PipelineTemplateStatus status, UUID id);
}
