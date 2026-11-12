package com.talentpipe.job.service;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.job.dto.JobStageResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PipelineSnapshotService} over the pipeline module's public service:
 * the template is read as a DTO, never as an entity, so the job module depends
 * only on what the pipeline module chooses to expose.
 */
@Service
public class PipelineSnapshotServiceImpl implements PipelineSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(PipelineSnapshotServiceImpl.class);

    static final String TEMPLATE_ARCHIVED =
            "The selected pipeline template is archived and cannot be used for new jobs.";
    static final String THRESHOLDS_OUT_OF_ORDER = "The manual threshold must be lower than the auto threshold.";

    /** Archived templates are read too, so choosing one is refused for being archived rather than reported missing. */
    private static final boolean INCLUDING_ARCHIVED = true;

    private final PipelineTemplateService templates;
    private final JobStageRepository stageRepository;
    private final VacancyAccess access;
    private final JobVacancyMapper mapper;

    public PipelineSnapshotServiceImpl(PipelineTemplateService templates,
                                       JobStageRepository stageRepository,
                                       VacancyAccess access,
                                       JobVacancyMapper mapper) {
        this.templates = templates;
        this.stageRepository = stageRepository;
        this.access = access;
        this.mapper = mapper;
    }

    @Override
    public PipelineSnapshot resolve(UUID tenantId, PipelineSelection selection) {
        if (selection.templateId() == null) {
            throw new InvalidRequestException(JobVacancyRequest.PIPELINE_TEMPLATE_REQUIRED);
        }
        PipelineTemplateResponse template = templates.get(tenantId, selection.templateId(), INCLUDING_ARCHIVED);
        if (template.status() == PipelineTemplateStatus.ARCHIVED) {
            throw new BusinessRuleException(TEMPLATE_ARCHIVED);
        }

        int autoThreshold = Objects.requireNonNullElse(selection.autoThreshold(), template.defaultAutoThreshold());
        int manualThreshold = Objects.requireNonNullElse(selection.manualThreshold(), template.defaultManualThreshold());
        boolean blindMode = Objects.requireNonNullElse(selection.blindModeEnabled(), template.blindModeEnabled());
        // Same rule as the template's own thresholds; an override of one can break it against the other.
        if (manualThreshold >= autoThreshold) {
            throw new BusinessRuleException(THRESHOLDS_OUT_OF_ORDER);
        }
        return new PipelineSnapshot(template.id(), template.name(), autoThreshold, manualThreshold, blindMode,
                template.stages());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void copyStages(JobVacancy vacancy, PipelineSnapshot snapshot) {
        List<JobStage> copies = snapshot.stages().stream()
                .sorted(Comparator.comparingInt(PipelineStageResponse::position))
                .map(stage -> new JobStage(vacancy.getTenantId(), vacancy.getId(), stage.position(),
                        stage.stageType(), stage.name(), stage.benchmark()))
                .toList();
        // Flushed here so a refused insert fails this call, not the commit after it.
        stageRepository.saveAllAndFlush(copies);
        log.info("Vacancy {} put on pipeline template {}: {} stages copied [tenant={}]",
                vacancy.getId(), snapshot.templateId(), copies.size(), vacancy.getTenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JobStageResponse> stages(VacancyViewer viewer, UUID vacancyId) {
        JobVacancy vacancy = access.require(viewer, vacancyId);
        return stageRepository.findAllByJobIdOrderByPositionAsc(vacancy.getId()).stream()
                .map(mapper::toStageResponse)
                .toList();
    }
}
