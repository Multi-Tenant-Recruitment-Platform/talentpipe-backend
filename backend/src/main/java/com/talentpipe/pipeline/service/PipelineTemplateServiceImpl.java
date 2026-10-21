package com.talentpipe.pipeline.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateSummaryResponse;
import com.talentpipe.pipeline.entity.PipelineTemplate;
import com.talentpipe.pipeline.entity.PipelineTemplateStage;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.mapper.PipelineTemplateMapper;
import com.talentpipe.pipeline.repository.PipelineTemplateRepository;
import com.talentpipe.pipeline.validator.PipelineTemplateValidator;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of pipeline template lifecycle and query operations.
 */
@Service
public class PipelineTemplateServiceImpl implements PipelineTemplateService {

    private static final String TEMPLATE_NOT_FOUND = "Pipeline template not found";

    private final PipelineTemplateRepository templateRepository;
    private final PipelineTemplateValidator validator;
    private final PipelineTemplateMapper mapper;

    public PipelineTemplateServiceImpl(PipelineTemplateRepository templateRepository,
                                       PipelineTemplateValidator validator,
                                       PipelineTemplateMapper mapper) {
        this.templateRepository = templateRepository;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public PipelineTemplateResponse create(UUID tenantId, UUID userId, PipelineTemplateRequest request) {
        validator.validate(tenantId, null, request);
        PipelineTemplate template = buildTemplateEntity(tenantId, userId, request);
        buildAndAttachStages(tenantId, template, request.stages());

        PipelineTemplate saved = templateRepository.save(template);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PipelineTemplateResponse get(UUID tenantId, UUID id, boolean isAdmin) {
        PipelineTemplate template = findTemplateOrThrow(tenantId, id);
        if (!isAdmin && template.isArchived()) {
            throw new ResourceNotFoundException(TEMPLATE_NOT_FOUND);
        }
        return mapper.toResponse(template);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PipelineTemplateSummaryResponse> list(
            UUID tenantId, String search, boolean includeArchived, boolean isAdmin, Pageable pageable) {
        boolean canIncludeArchived = isAdmin && includeArchived;
        String query = search == null ? "" : search.trim();
        Page<PipelineTemplate> page = queryTemplates(tenantId, query, canIncludeArchived, pageable);
        return page.map(mapper::toSummaryResponse);
    }

    @Override
    @Transactional
    public PipelineTemplateResponse update(UUID tenantId, UUID userId, UUID id, PipelineTemplateRequest request) {
        PipelineTemplate template = findTemplateOrThrow(tenantId, id);
        if (template.isArchived()) {
            throw new BusinessRuleException("Cannot edit an archived pipeline template");
        }
        validator.validate(tenantId, id, request);
        applyTemplateUpdates(template, request);
        replaceStages(tenantId, template, request.stages());

        PipelineTemplate saved = templateRepository.save(template);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public PipelineTemplateResponse archive(UUID tenantId, UUID id) {
        PipelineTemplate template = findTemplateOrThrow(tenantId, id);
        if (!template.isArchived()) {
            template.archive();
            template = templateRepository.save(template);
        }
        return mapper.toResponse(template);
    }

    @Override
    @Transactional
    public PipelineTemplateResponse duplicate(UUID tenantId, UUID userId, UUID id) {
        PipelineTemplate source = findTemplateOrThrow(tenantId, id);
        String copyName = generateCopyName(tenantId, source.getName());

        PipelineTemplate duplicate = new PipelineTemplate(
                tenantId, copyName, source.getDescription(),
                source.getDefaultAutoThreshold(), source.getDefaultManualThreshold(),
                source.isIdealMatchingEnabled(), userId
        );
        duplicateStages(tenantId, source, duplicate);

        PipelineTemplate saved = templateRepository.save(duplicate);
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void delete(UUID tenantId, UUID id) {
        PipelineTemplate template = findTemplateOrThrow(tenantId, id);
        templateRepository.delete(template);
    }

    private PipelineTemplate findTemplateOrThrow(UUID tenantId, UUID id) {
        return templateRepository.findWithStagesByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException(TEMPLATE_NOT_FOUND));
    }

    private PipelineTemplate buildTemplateEntity(UUID tenantId, UUID userId, PipelineTemplateRequest req) {
        return new PipelineTemplate(
                tenantId,
                validator.sanitizeText(req.name()),
                validator.sanitizeText(req.description()),
                req.defaultAutoThreshold(),
                req.defaultManualThreshold(),
                Boolean.TRUE.equals(req.idealMatchingEnabled()),
                userId
        );
    }

    private void applyTemplateUpdates(PipelineTemplate template, PipelineTemplateRequest req) {
        template.setName(validator.sanitizeText(req.name()));
        template.setDescription(validator.sanitizeText(req.description()));
        template.setDefaultAutoThreshold(req.defaultAutoThreshold());
        template.setDefaultManualThreshold(req.defaultManualThreshold());
        template.setIdealMatchingEnabled(Boolean.TRUE.equals(req.idealMatchingEnabled()));
    }

    private void buildAndAttachStages(UUID tenantId, PipelineTemplate template, List<PipelineStageRequest> stageReqs) {
        stageReqs.stream()
                .sorted(Comparator.comparingInt(PipelineStageRequest::position))
                .forEach(stageReq -> {
                    String cleanStageName = validator.sanitizeText(stageReq.name());
                    PipelineTemplateStage stage = mapper.toStageEntity(tenantId, stageReq, cleanStageName);
                    template.addStage(stage);
                });
    }

    private void replaceStages(UUID tenantId, PipelineTemplate template, List<PipelineStageRequest> stageReqs) {
        List<PipelineTemplateStage> newStages = stageReqs.stream()
                .sorted(Comparator.comparingInt(PipelineStageRequest::position))
                .map(stageReq -> {
                    String cleanName = validator.sanitizeText(stageReq.name());
                    return mapper.toStageEntity(tenantId, stageReq, cleanName);
                })
                .toList();
        template.setStages(newStages);
    }

    private void duplicateStages(UUID tenantId, PipelineTemplate source, PipelineTemplate duplicate) {
        source.getStages().stream()
                .sorted(Comparator.comparingInt(PipelineTemplateStage::getPosition))
                .forEach(stage -> duplicate.addStage(new PipelineTemplateStage(
                        tenantId, stage.getPosition(), stage.getStageType(), stage.getName(), stage.getBenchmark())));
    }

    private Page<PipelineTemplate> queryTemplates(
            UUID tenantId, String query, boolean includeArchived, Pageable pageable) {
        if (!query.isEmpty()) {
            return includeArchived
                    ? templateRepository.findByTenantIdAndNameContainingIgnoreCase(tenantId, query, pageable)
                    : templateRepository.findByTenantIdAndStatusAndNameContainingIgnoreCase(
                            tenantId, PipelineTemplateStatus.ACTIVE, query, pageable);
        }
        return includeArchived
                ? templateRepository.findByTenantId(tenantId, pageable)
                : templateRepository.findByTenantIdAndStatus(tenantId, PipelineTemplateStatus.ACTIVE, pageable);
    }

    private String generateCopyName(UUID tenantId, String baseName) {
        String candidate = baseName + " (Copy)";
        if (!templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                tenantId, candidate, PipelineTemplateStatus.ACTIVE)) {
            return candidate;
        }
        int index = 2;
        while (templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                tenantId, baseName + " (Copy " + index + ")", PipelineTemplateStatus.ACTIVE)) {
            index++;
        }
        return baseName + " (Copy " + index + ")";
    }
}
