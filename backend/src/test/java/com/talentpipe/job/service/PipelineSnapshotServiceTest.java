package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.InvalidRequestException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.JobStageResponse;
import com.talentpipe.job.dto.JobVacancyRequest;
import com.talentpipe.job.entity.VacancyPipeline;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.mapper.JobVacancyMapper;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.pipeline.PipelineTemplateFixtures;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.dto.PipelineTemplateResponse;
import com.talentpipe.pipeline.entity.PipelineTemplateStatus;
import com.talentpipe.pipeline.entity.StageType;
import com.talentpipe.pipeline.service.PipelineTemplateService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link PipelineSnapshotServiceImpl}: every inheritance,
 * override and failure branch of putting a new vacancy on a template's
 * pipeline. Pure Mockito; the pipeline module is reached only through its
 * service, so that is what is mocked.
 */
@ExtendWith(MockitoExtension.class)
class PipelineSnapshotServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID TEMPLATE = UUID.randomUUID();
    private static final VacancyViewer VIEWER = new VacancyViewer(TENANT, ACTOR, true);

    @Mock
    private PipelineTemplateService templates;

    @Mock
    private JobStageRepository stageRepository;

    @Mock
    private JobVacancyRepository vacancyRepository;

    @Captor
    private ArgumentCaptor<List<JobStage>> savedStages;

    private PipelineSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PipelineSnapshotServiceImpl(
                templates, stageRepository, new VacancyAccess(vacancyRepository), new JobVacancyMapper());
    }

    /** The template the tests choose: 80/70 with blind mode as given, active unless said otherwise. */
    private PipelineTemplateResponse template(boolean blindMode, PipelineTemplateStatus status) {
        PipelineTemplateResponse template = PipelineTemplateFixtures.templateResponse(TEMPLATE, 80, 70, blindMode, status);
        when(templates.get(TENANT, TEMPLATE, true)).thenReturn(template);
        return template;
    }

    private PipelineTemplateResponse template(boolean blindMode) {
        return template(blindMode, PipelineTemplateStatus.ACTIVE);
    }

    private static PipelineSelection choose(Integer auto, Integer manual, Boolean blindMode) {
        return new PipelineSelection(TEMPLATE, auto, manual, blindMode);
    }

    @Nested
    class Inheritance {

        @Test
        void withNoOverrides_theVacancyStartsWithEveryOneOfTheTemplatesSettings() {
            PipelineTemplateResponse template = template(true);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(null, null, null));

            assertThat(snapshot.templateId()).isEqualTo(TEMPLATE);
            assertThat(snapshot.templateName()).isEqualTo(template.name());
            assertThat(snapshot.autoThreshold()).isEqualTo(80);
            assertThat(snapshot.manualThreshold()).isEqualTo(70);
            assertThat(snapshot.blindModeEnabled()).isTrue();
            assertThat(snapshot.stages()).isEqualTo(template.stages());
            assertThat(snapshot.stageCount()).isEqualTo(4);
            assertThat(snapshot.interviewRoundCount()).isEqualTo(1);
        }

        @Test
        void blindModeOff_onTheTemplate_isInheritedAsOff() {
            template(false);

            assertThat(service.resolve(TENANT, choose(null, null, null)).blindModeEnabled()).isFalse();
        }

        @Test
        void archivedTemplatesAreRead_soTheyCanBeRefusedAsArchivedRatherThanMissing() {
            template(false);

            service.resolve(TENANT, choose(null, null, null));

            verify(templates).get(TENANT, TEMPLATE, true);
        }
    }

    @Nested
    class Overrides {

        @Test
        void bothThresholds_replaceTheTemplates_andTheTemplateIsOnlyRead() {
            template(false);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(90, 65, null));

            assertThat(snapshot.autoThreshold()).isEqualTo(90);
            assertThat(snapshot.manualThreshold()).isEqualTo(65);
            verify(templates).get(TENANT, TEMPLATE, true);
            verifyNoMoreInteractions(templates);
        }

        @Test
        void onlyTheAutoThreshold_keepsTheTemplatesManualThreshold() {
            template(false);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(95, null, null));

            assertThat(snapshot.autoThreshold()).isEqualTo(95);
            assertThat(snapshot.manualThreshold()).isEqualTo(70);
        }

        @Test
        void onlyTheManualThreshold_keepsTheTemplatesAutoThreshold() {
            template(false);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(null, 40, null));

            assertThat(snapshot.autoThreshold()).isEqualTo(80);
            assertThat(snapshot.manualThreshold()).isEqualTo(40);
        }

        @ParameterizedTest
        @CsvSource({"true, false", "false, true"})
        void blindMode_givenOnTheRequest_winsOverTheTemplates(boolean onTemplate, boolean onRequest) {
            template(onTemplate);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(null, null, onRequest));

            assertThat(snapshot.blindModeEnabled()).isEqualTo(onRequest);
        }

        @Test
        void theExtremesOfTheScale_areAccepted() {
            template(false);

            PipelineSnapshot snapshot = service.resolve(TENANT, choose(100, 0, null));

            assertThat(snapshot.autoThreshold()).isEqualTo(100);
            assertThat(snapshot.manualThreshold()).isZero();
        }
    }

    @Nested
    class Failures {

        @Test
        void noTemplate_isABadRequest_andThePipelineModuleIsNeverAsked() {
            PipelineSelection selection = new PipelineSelection(null, 90, 65, true);

            assertThatThrownBy(() -> service.resolve(TENANT, selection))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessage(JobVacancyRequest.PIPELINE_TEMPLATE_REQUIRED);

            verifyNoInteractions(templates);
        }

        @Test
        void anArchivedTemplate_isRefusedWithTheExactMessage() {
            template(true, PipelineTemplateStatus.ARCHIVED);
            PipelineSelection selection = choose(null, null, null);

            assertThatThrownBy(() -> service.resolve(TENANT, selection))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("The selected pipeline template is archived and cannot be used for new jobs.");
        }

        @Test
        void anArchivedTemplate_isRefusedEvenWithEveryOverrideGiven() {
            template(false, PipelineTemplateStatus.ARCHIVED);
            PipelineSelection selection = choose(90, 65, true);

            assertThatThrownBy(() -> service.resolve(TENANT, selection))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage(PipelineSnapshotServiceImpl.TEMPLATE_ARCHIVED);
        }

        @Test
        void aMissingOrAnotherCompanysTemplate_isNotFound_asThePipelineModuleReportsIt() {
            when(templates.get(TENANT, TEMPLATE, true))
                    .thenThrow(new ResourceNotFoundException("Pipeline template not found"));
            PipelineSelection selection = choose(null, null, null);

            assertThatThrownBy(() -> service.resolve(TENANT, selection))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Pipeline template not found");
        }

        @ParameterizedTest
        @CsvSource(value = {
                "NULL, 80",   // manual override equal to the template's auto threshold
                "NULL, 85",   // manual override above it
                "70, NULL",   // auto override equal to the template's manual threshold
                "60, NULL",   // auto override below it
                "50, 50",     // both given, equal
                "40, 60",     // both given, the wrong way round
        }, nullValues = "NULL")
        void thresholdsThatEndUpOutOfOrder_areRefused(Integer auto, Integer manual) {
            template(false);
            PipelineSelection selection = choose(auto, manual, null);

            assertThatThrownBy(() -> service.resolve(TENANT, selection))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessage("The manual threshold must be lower than the auto threshold.");
        }
    }

    @Nested
    class Summary {

        private final PipelineSnapshot twoRounds = new PipelineSnapshot(TEMPLATE, "Engineering", 90, 60, true, List.of(
                new PipelineStageResponse(UUID.randomUUID(), 0, StageType.POST_APPLICATION, "Applied", null),
                new PipelineStageResponse(UUID.randomUUID(), 1, StageType.INTERVIEW, "Technical", 70),
                new PipelineStageResponse(UUID.randomUUID(), 2, StageType.INTERVIEW, "Panel", 75),
                new PipelineStageResponse(UUID.randomUUID(), 3, StageType.ONBOARD, "Onboard", null)));

        @Test
        void countsEveryStage_andOnlyInterviewRoundsAsRounds() {
            assertThat(twoRounds.stageCount()).isEqualTo(4);
            assertThat(twoRounds.interviewRoundCount()).isEqualTo(2);
        }

        @Test
        void whatTheVacancyStores_isTheSnapshotsSettingsAndShape() {
            assertThat(twoRounds.toVacancyPipeline())
                    .isEqualTo(new VacancyPipeline(TEMPLATE, "Engineering", 90, 60, true, 4, 2));
        }
    }

    @Nested
    class CopyingStages {

        @Test
        void everyStageIsCopiedExactly_ontoTheVacancy_inTheVacancysTenant() {
            JobVacancy vacancy = VacancyFixtures.draft(TENANT);
            PipelineSnapshot snapshot = new PipelineSnapshot(TEMPLATE, "Standard", 80, 70, false,
                    PipelineTemplateFixtures.sampleResponse(TEMPLATE).stages());

            service.copyStages(vacancy, snapshot);

            verify(stageRepository).saveAllAndFlush(savedStages.capture());
            assertThat(savedStages.getValue())
                    .extracting(JobStage::getTenantId, JobStage::getJobId, JobStage::getPosition,
                            JobStage::getStageType, JobStage::getName, JobStage::getBenchmark)
                    .containsExactly(
                            tuple(TENANT, vacancy.getId(), 0, StageType.POST_APPLICATION, "Post Application", null),
                            tuple(TENANT, vacancy.getId(), 1, StageType.SCREENING, "Screening", null),
                            tuple(TENANT, vacancy.getId(), 2, StageType.INTERVIEW, "Technical Round", 75),
                            tuple(TENANT, vacancy.getId(), 3, StageType.ONBOARD, "Onboard", null));
        }

        @Test
        void stagesAreStoredInPipelineOrder_whateverOrderTheyArriveIn() {
            JobVacancy vacancy = VacancyFixtures.draft(TENANT);
            PipelineSnapshot snapshot = new PipelineSnapshot(TEMPLATE, "Standard", 80, 70, false, List.of(
                    new PipelineStageResponse(UUID.randomUUID(), 2, StageType.ONBOARD, "Onboard", null),
                    new PipelineStageResponse(UUID.randomUUID(), 0, StageType.POST_APPLICATION, "Applied", null),
                    new PipelineStageResponse(UUID.randomUUID(), 1, StageType.INTERVIEW, "Panel", 60)));

            service.copyStages(vacancy, snapshot);

            verify(stageRepository).saveAllAndFlush(savedStages.capture());
            assertThat(savedStages.getValue()).extracting(JobStage::getPosition).containsExactly(0, 1, 2);
        }
    }

    @Nested
    class ReadingStages {

        @Test
        void theVacancysStagesAreReturnedFirstToLast() {
            JobVacancy vacancy = VacancyFixtures.draft(TENANT);
            when(vacancyRepository.findByIdAndTenantId(vacancy.getId(), TENANT)).thenReturn(Optional.of(vacancy));
            when(stageRepository.findAllByJobIdOrderByPositionAsc(vacancy.getId())).thenReturn(List.of(
                    new JobStage(TENANT, vacancy.getId(), 0, StageType.POST_APPLICATION, "Applied", null),
                    new JobStage(TENANT, vacancy.getId(), 1, StageType.INTERVIEW, "Panel", 60)));

            List<JobStageResponse> stages = service.stages(VIEWER, vacancy.getId());

            assertThat(stages)
                    .extracting(JobStageResponse::position, JobStageResponse::stageType,
                            JobStageResponse::name, JobStageResponse::benchmark)
                    .containsExactly(
                            tuple(0, StageType.POST_APPLICATION, "Applied", null),
                            tuple(1, StageType.INTERVIEW, "Panel", 60));
        }

        @Test
        void aVacancyTheViewerCannotSee_isNotFound_andNoStageIsRead() {
            UUID vacancyId = UUID.randomUUID();
            when(vacancyRepository.findByIdAndTenantId(vacancyId, TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.stages(VIEWER, vacancyId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessage("Vacancy not found");

            verify(stageRepository, never()).findAllByJobIdOrderByPositionAsc(any());
        }
    }

    @Test
    void resolvingOnlyReadsTheTemplate_andWritesNothing() {
        template(true);

        service.resolve(TENANT, choose(90, 65, false));

        verify(templates).get(TENANT, TEMPLATE, true);
        verifyNoMoreInteractions(templates);
        verifyNoInteractions(stageRepository, vacancyRepository);
    }
}
