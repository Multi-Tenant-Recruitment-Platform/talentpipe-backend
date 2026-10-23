package com.talentpipe.pipeline.validator;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.util.HtmlSanitizer;
import com.talentpipe.pipeline.PipelineTemplateFixtures;
import com.talentpipe.pipeline.dto.PipelineStageRequest;
import com.talentpipe.pipeline.dto.PipelineTemplateRequest;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.entity.StageType;
import com.talentpipe.pipeline.repository.PipelineTemplateRepository;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PipelineTemplateValidatorTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private PipelineTemplateRepository templateRepository;

    private PipelineTemplateValidator validator;

    @BeforeEach
    void setUp() {
        validator = new PipelineTemplateValidator(templateRepository, new HtmlSanitizer());
    }

    @Test
    void validate_validRequest_succeeds() {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, "Standard Engineering Pipeline", PipelineTemplateStatus.ACTIVE))
                .thenReturn(false);

        assertDoesNotThrow(() -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_validRequestWithoutScreening_succeeds() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Technical Round", 75),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );
        PipelineTemplateRequest request = new PipelineTemplateRequest(
                "Short Pipeline", "Direct interview", 85, 65, false, stages);

        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, "Short Pipeline", PipelineTemplateStatus.ACTIVE))
                .thenReturn(false);

        assertDoesNotThrow(() -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_blankName_throwsInvalidRequestException() {
        PipelineTemplateRequest request = new PipelineTemplateRequest(
                "   ", "desc", 80, 70, true, PipelineTemplateFixtures.standardStages());

        assertThrows(InvalidRequestException.class, () -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_nameWithHtmlOnly_throwsInvalidRequestException() {
        PipelineTemplateRequest request = new PipelineTemplateRequest(
                "<b></b>", "desc", 80, 70, true, PipelineTemplateFixtures.standardStages());

        assertThrows(InvalidRequestException.class, () -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_duplicateNameOnCreate_throwsDuplicateResourceException() {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatus(
                TENANT_ID, "Standard Engineering Pipeline", PipelineTemplateStatus.ACTIVE))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_duplicateNameOnUpdate_throwsDuplicateResourceException() {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatusAndIdNot(
                TENANT_ID, "Standard Engineering Pipeline", PipelineTemplateStatus.ACTIVE, TEMPLATE_ID))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> validator.validate(TENANT_ID, TEMPLATE_ID, request));
    }

    @Test
    void validate_sameNameOnUpdate_succeeds() {
        PipelineTemplateRequest request = PipelineTemplateFixtures.validRequest();
        when(templateRepository.existsByTenantIdAndNameIgnoreCaseAndStatusAndIdNot(
                TENANT_ID, "Standard Engineering Pipeline", PipelineTemplateStatus.ACTIVE, TEMPLATE_ID))
                .thenReturn(false);

        assertDoesNotThrow(() -> validator.validate(TENANT_ID, TEMPLATE_ID, request));
    }

    @Test
    void validate_autoThresholdLowerThanManual_throwsBusinessRuleException() {
        PipelineTemplateRequest request = new PipelineTemplateRequest(
                "Name", "desc", 60, 70, true, PipelineTemplateFixtures.standardStages());

        assertThrows(BusinessRuleException.class, () -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_autoThresholdEqualToManual_throwsBusinessRuleException() {
        PipelineTemplateRequest request = new PipelineTemplateRequest(
                "Name", "desc", 70, 70, true, PipelineTemplateFixtures.standardStages());

        assertThrows(BusinessRuleException.class, () -> validator.validate(TENANT_ID, null, request));
    }

    @Test
    void validate_emptyStages_throwsBusinessRuleException() {
        List<PipelineStageRequest> empty = Collections.emptyList();
        assertThrows(BusinessRuleException.class, () -> validator.validateStages(empty));
    }

    @Test
    void validate_nonConsecutivePositions_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(2, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(3, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_firstStageNotPostApplication_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.SCREENING, "Screening", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_lastStageNotOnboard_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", 80)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_duplicatePostApplication_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.POST_APPLICATION, "Another App", null),
                new PipelineStageRequest(2, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(3, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_multipleScreeningStages_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.SCREENING, "Screening 1", null),
                new PipelineStageRequest(2, StageType.SCREENING, "Screening 2", null),
                new PipelineStageRequest(3, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(4, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_noInterviewRounds_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.SCREENING, "Screening", null),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_interviewRoundWithoutBenchmark_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", null),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @ParameterizedTest
    @ValueSource(ints = {-5, 101, 150})
    void validate_interviewRoundInvalidBenchmark_throwsBusinessRuleException(int benchmark) {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", benchmark),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_nonInterviewStageWithBenchmark_throwsBusinessRuleException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "Post Application", 50),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(BusinessRuleException.class, () -> validator.validateStages(stages));
    }

    @Test
    void validate_stageWithEmptyName_throwsInvalidRequestException() {
        List<PipelineStageRequest> stages = List.of(
                new PipelineStageRequest(0, StageType.POST_APPLICATION, "  ", null),
                new PipelineStageRequest(1, StageType.INTERVIEW, "Round", 80),
                new PipelineStageRequest(2, StageType.ONBOARD, "Onboard", null)
        );

        assertThrows(InvalidRequestException.class, () -> validator.validateStages(stages));
    }

    @Test
    void sanitizeText_nullInput_returnsEmptyString() {
        assertEquals("", validator.sanitizeText(null));
    }
}
