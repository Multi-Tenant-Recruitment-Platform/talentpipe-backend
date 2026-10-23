package com.talentpipe.pipeline.service;

import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Service contract for managing recruitment pipeline templates.
 */
public interface PipelineTemplateService {

    PipelineTemplateResponse create(UUID tenantId, UUID userId, PipelineTemplateRequest request);

    PipelineTemplateResponse get(UUID tenantId, UUID id, boolean isAdmin);

    Page<PipelineTemplateSummaryResponse> list(
            UUID tenantId, String search, boolean includeArchived, boolean isAdmin, Pageable pageable);

    PipelineTemplateResponse update(UUID tenantId, UUID userId, UUID id, PipelineTemplateRequest request);

    PipelineTemplateResponse archive(UUID tenantId, UUID id);

    PipelineTemplateResponse duplicate(UUID tenantId, UUID userId, UUID id);

    void delete(UUID tenantId, UUID id);
}
