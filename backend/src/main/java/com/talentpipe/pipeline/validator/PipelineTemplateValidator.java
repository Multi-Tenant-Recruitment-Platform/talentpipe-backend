package com.talentpipe.pipeline.validator;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.entity.StageType;
import com.talentpipe.pipeline.repository.PipelineTemplateRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Validates pipeline template structural and business rules.
 */
@Component
public class PipelineTemplateValidator {

    private static final String DUPLICATE_NAME_MSG = "A template with this name already exists";
    private static final String BENCHMARK_RANGE_MSG =
            "Benchmark is required for interview rounds and must be between 0 and 100";
    private static final int MIN_SCORE = 0;
    private static final int MAX_SCORE = 100;

    private final PipelineTemplateRepository templateRepository;
    private final HtmlSanitizer htmlSanitizer;

    public PipelineTemplateValidator(PipelineTemplateRepository templateRepository,
                                     HtmlSanitizer htmlSanitizer) {
        this.templateRepository = templateRepository;
        this.htmlSanitizer = htmlSanitizer;
    }

    public void validate(UUID tenantId, UUID templateId, PipelineTemplateRequest request) {
        validateName(tenantId, templateId, request.name());
        validateThresholds(request.defaultAutoThreshold(), request.defaultManualThreshold());
        validateStages(request.stages());
    }

    public void validateName(UUID tenantId, UUID templateId, String rawName) {
        String sanitizedName = sanitizeText(rawName);
        if (sanitizedName.isEmpty()) {
            throw new InvalidRequestException("Template name cannot be empty");
        }
        boolean isDuplicate = templateId == null
                ? templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                        tenantId, sanitizedName, PipelineTemplateStatus.ACTIVE)
                : templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatusAndIdNot(
                        tenantId, sanitizedName, PipelineTemplateStatus.ACTIVE, templateId);
        if (isDuplicate) {
            throw new DuplicateResourceException(DUPLICATE_NAME_MSG);
        }
    }

    public void validateThresholds(int autoThreshold, int manualThreshold) {
        if (manualThreshold >= autoThreshold) {
            throw new BusinessRuleException("Manual threshold must be lower than auto threshold");
        }
    }

    public void validateStages(List<PipelineStageRequest> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new BusinessRuleException("Stages cannot be empty");
        }
        List<PipelineStageRequest> sortedStages = stages.stream()
                .sorted(Comparator.comparingInt(PipelineStageRequest::position))
                .toList();

        validateConsecutivePositions(sortedStages);
        validateStageOrderAndCounts(sortedStages);
        sortedStages.forEach(this::validateIndividualStage);
    }

    private void validateConsecutivePositions(List<PipelineStageRequest> sortedStages) {
        for (int i = 0; i < sortedStages.size(); i++) {
            if (sortedStages.get(i).position() != i) {
                throw new BusinessRuleException("Stage positions must be consecutive starting from 0");
            }
        }
    }

    private void validateStageOrderAndCounts(List<PipelineStageRequest> sortedStages) {
        if (sortedStages.get(0).stageType() != StageType.POST_APPLICATION) {
            throw new BusinessRuleException("The first stage must be Post Application");
        }
        int lastIndex = sortedStages.size() - 1;
        if (sortedStages.get(lastIndex).stageType() != StageType.ONBOARD) {
            throw new BusinessRuleException("The last stage must be Onboard");
        }

        long postAppCount = countStageType(sortedStages, StageType.POST_APPLICATION);
        long onboardCount = countStageType(sortedStages, StageType.ONBOARD);
        long screeningCount = countStageType(sortedStages, StageType.SCREENING);
        long interviewCount = countStageType(sortedStages, StageType.INTERVIEW);

        if (postAppCount != 1 || onboardCount != 1) {
            throw new BusinessRuleException("Pipeline must have exactly one Post Application and one Onboard stage");
        }
        if (screeningCount > 1) {
            throw new BusinessRuleException("At most one Screening stage is allowed");
        }
        if (interviewCount < 1) {
            throw new BusinessRuleException("At least one interview round is required");
        }
    }

    private long countStageType(List<PipelineStageRequest> stages, StageType type) {
        return stages.stream().filter(s -> s.stageType() == type).count();
    }

    private void validateIndividualStage(PipelineStageRequest stage) {
        String cleanName = sanitizeText(stage.name());
        if (cleanName.isEmpty()) {
            throw new InvalidRequestException("Stage name cannot be empty");
        }
        if (stage.stageType() == StageType.INTERVIEW) {
            validateInterviewBenchmark(stage.benchmark());
        } else if (stage.benchmark() != null) {
            throw new BusinessRuleException("Benchmark is only allowed for interview rounds");
        }
    }

    private void validateInterviewBenchmark(Integer benchmark) {
        if (benchmark == null || benchmark < MIN_SCORE || benchmark > MAX_SCORE) {
            throw new BusinessRuleException(BENCHMARK_RANGE_MSG);
        }
    }

    public String sanitizeText(String input) {
        if (input == null) {
            return "";
        }
        String sanitized = htmlSanitizer.sanitize(input);
        return sanitized == null ? "" : sanitized.trim();
    }
}
