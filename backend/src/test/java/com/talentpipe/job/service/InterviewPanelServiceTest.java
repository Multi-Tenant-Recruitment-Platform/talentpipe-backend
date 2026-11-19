package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.dto.MemberSummary;
import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.InterviewPanelRequest;
import com.talentpipe.job.dto.InterviewPanelResponse;
import com.talentpipe.job.entity.JobStage;
import com.talentpipe.job.entity.JobStageInterviewer;
import com.talentpipe.job.entity.JobStagePanel;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.repository.JobStageInterviewerRepository;
import com.talentpipe.job.repository.JobStagePanelRepository;
import com.talentpipe.job.repository.JobStageRepository;
import com.talentpipe.pipeline.dto.PipelineStageResponse;
import com.talentpipe.pipeline.entity.StageType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for interview panels: what may be stored on a panel in any
 * state, what publishing needs, and how a vacancy's panels read back.
 */
@ExtendWith(MockitoExtension.class)
class InterviewPanelServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final VacancyViewer VIEWER = new VacancyViewer(TENANT, ACTOR, true);

    @Mock
    private VacancyAccess access;
    @Mock
    private JobStageRepository stageRepository;
    @Mock
    private JobStagePanelRepository panelRepository;
    @Mock
    private JobStageInterviewerRepository interviewerRepository;
    @Mock
    private UserDirectoryService userDirectory;
    @Captor
    private ArgumentCaptor<JobStagePanel> savedPanel;
    @Captor
    private ArgumentCaptor<JobStageInterviewer> savedInterviewer;
    @Captor
    private ArgumentCaptor<Collection<JobStageInterviewer>> removedInterviewers;

    private InterviewPanelService service;
    private JobVacancy vacancy;
    private JobStage technical;
    private JobStage culture;

    @BeforeEach
    void setUp() {
        service = new InterviewPanelService(access, stageRepository, panelRepository, interviewerRepository,
                userDirectory);
        vacancy = VacancyFixtures.draftOnPipeline(TENANT);
        technical = stage(2, StageType.INTERVIEW, "Technical interview");
        culture = stage(3, StageType.INTERVIEW, "Culture interview");
    }

    // ------------------------------------------------------------------ apply

    @Test
    void apply_storesTheCountAndThePeople() {
        UUID ada = UUID.randomUUID();
        UUID grace = UUID.randomUUID();
        pipelineOf(stage(0, StageType.POST_APPLICATION, "Applied"), technical);
        when(userDirectory.activeStaffAmong(TENANT, Set.of(ada, grace))).thenReturn(Set.of(ada, grace));

        service.apply(vacancy, List.of(new InterviewPanelRequest(2, 2, List.of(ada, grace, ada))), ACTOR);

        verify(panelRepository).save(savedPanel.capture());
        assertThat(savedPanel.getValue().getJobStageId()).isEqualTo(technical.getId());
        assertThat(savedPanel.getValue().getInterviewerCount()).isEqualTo(2);
        verify(interviewerRepository, times(2)).save(savedInterviewer.capture());
        // A repeat is ignored, as on the hiring team.
        assertThat(savedInterviewer.getAllValues()).extracting(JobStageInterviewer::getUserId)
                .containsExactly(ada, grace);
        assertThat(savedInterviewer.getAllValues()).allSatisfy(row -> {
            assertThat(row.getJobId()).isEqualTo(vacancy.getId());
            assertThat(row.getTenantId()).isEqualTo(TENANT);
        });
    }

    @Test
    void apply_aDraftMayHaveFewerPeopleThanItsCount() {
        UUID ada = UUID.randomUUID();
        pipelineOf(technical);
        when(userDirectory.activeStaffAmong(TENANT, Set.of(ada))).thenReturn(Set.of(ada));

        assertThatCode(() -> service.apply(vacancy, List.of(new InterviewPanelRequest(2, 3, List.of(ada))), ACTOR))
                .doesNotThrowAnyException();
    }

    @Test
    void apply_refusesAStageThatIsNotAnInterviewRound() {
        pipelineOf(stage(1, StageType.SCREENING, "Screening"), technical);

        assertThatThrownBy(() -> service.apply(vacancy,
                List.of(new InterviewPanelRequest(1, 1, List.of(UUID.randomUUID()))), ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(InterviewPanelService.NOT_AN_INTERVIEW_ROUND);
        verify(panelRepository, never()).save(any());
    }

    @Test
    void apply_refusesTheSameRoundTwice() {
        pipelineOf(technical);

        assertThatThrownBy(() -> service.apply(vacancy, List.of(
                new InterviewPanelRequest(2, 1, List.of()),
                new InterviewPanelRequest(2, 2, List.of())), ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(InterviewPanelService.ROUND_LISTED_TWICE);
    }

    @Test
    void apply_refusesPeopleBeforeACount() {
        pipelineOf(technical);

        assertThatThrownBy(() -> service.apply(vacancy,
                List.of(new InterviewPanelRequest(2, null, List.of(UUID.randomUUID()))), ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Set how many interviewers Technical interview needs before choosing them.");
    }

    @Test
    void apply_refusesMorePeopleThanTheCount_evenOnADraft() {
        pipelineOf(technical);

        assertThatThrownBy(() -> service.apply(vacancy, List.of(new InterviewPanelRequest(2, 1,
                List.of(UUID.randomUUID(), UUID.randomUUID()))), ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Technical interview has 2 interviewers but needs only 1. Remove some or raise the number.");
    }

    @Test
    void apply_refusesANewcomerWhoIsNotAnActiveMemberOfTheCompany() {
        UUID ours = UUID.randomUUID();
        UUID elsewhere = UUID.randomUUID();
        pipelineOf(technical);
        // Another company's user, a disabled account or an invitee is simply not among the active staff.
        when(userDirectory.activeStaffAmong(TENANT, Set.of(ours, elsewhere))).thenReturn(Set.of(ours));

        assertThatThrownBy(() -> service.apply(vacancy,
                List.of(new InterviewPanelRequest(2, 2, List.of(ours, elsewhere))), ACTOR))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(InterviewPanelService.NOT_ACTIVE_MEMBERS);
        verify(panelRepository, never()).save(any());
        verify(interviewerRepository, never()).save(any());
    }

    @Test
    void apply_keepsSomeoneAlreadySeated_whoHasSinceBeenDisabled() {
        UUID disabled = UUID.randomUUID();
        UUID newcomer = UUID.randomUUID();
        pipelineOf(technical);
        panelsOf(panel(technical, 2));
        seated(seat(technical, disabled));
        when(userDirectory.activeStaffAmong(TENANT, Set.of(newcomer))).thenReturn(Set.of(newcomer));

        service.apply(vacancy, List.of(new InterviewPanelRequest(2, 2, List.of(disabled, newcomer))), ACTOR);

        verify(interviewerRepository).save(savedInterviewer.capture());
        assertThat(savedInterviewer.getValue().getUserId()).isEqualTo(newcomer);
    }

    @Test
    void apply_takesOffThePeopleLeftOut_andDropsAPanelLeftOut() {
        UUID staying = UUID.randomUUID();
        UUID leaving = UUID.randomUUID();
        pipelineOf(technical, culture);
        JobStagePanel cultureMeta = panel(culture, 1);
        panelsOf(panel(technical, 2), cultureMeta);
        JobStageInterviewer leavingRow = seat(technical, leaving);
        JobStageInterviewer cultureRow = seat(culture, staying);
        seated(seat(technical, staying), leavingRow, cultureRow);
        when(userDirectory.activeStaffAmong(TENANT, Set.of())).thenReturn(Set.of());

        service.apply(vacancy, List.of(new InterviewPanelRequest(2, 2, List.of(staying))), ACTOR);

        List<JobStageInterviewer> removed = new ArrayList<>();
        verify(interviewerRepository, times(2)).deleteAll(removedInterviewers.capture());
        removedInterviewers.getAllValues().forEach(removed::addAll);
        assertThat(removed).containsExactlyInAnyOrder(leavingRow, cultureRow);
        verify(panelRepository).delete(cultureMeta);
    }

    @Test
    void apply_resizesAStoredPanel() {
        pipelineOf(technical);
        JobStagePanel stored = panel(technical, 1);
        panelsOf(stored);
        when(userDirectory.activeStaffAmong(TENANT, Set.of())).thenReturn(Set.of());

        service.apply(vacancy, List.of(new InterviewPanelRequest(2, 3, List.of())), ACTOR);

        assertThat(stored.getInterviewerCount()).isEqualTo(3);
        assertThat(stored.getUpdatedBy()).isEqualTo(ACTOR);
        verify(panelRepository, never()).save(any());
    }

    // ------------------------------------------------------------- replace

    @Test
    void replace_refusesAnArchivedVacancy() {
        JobVacancy archived = VacancyFixtures.archived(TENANT);
        when(access.require(VIEWER, archived.getId())).thenReturn(archived);

        assertThatThrownBy(() -> service.replace(VIEWER, archived.getId(), List.of()))
                .isInstanceOf(BusinessRuleException.class);
        verify(panelRepository, never()).findAllByJobId(any());
    }

    @Test
    void replace_onALiveVacancy_mustLeaveEveryRoundComplete() {
        JobVacancy live = VacancyFixtures.publishedOnPipeline(TENANT);
        vacancy = live;
        when(access.require(VIEWER, live.getId())).thenReturn(live);
        technical = stage(2, StageType.INTERVIEW, "Technical interview");
        pipelineOf(technical);
        when(userDirectory.activeStaffAmong(TENANT, Set.of())).thenReturn(Set.of());

        assertThatThrownBy(() -> service.replace(VIEWER, live.getId(), List.of()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage(InterviewPanelService.MUST_STAY_COMPLETE + ": Technical interview needs its interviewers.");
    }

    // ---------------------------------------------------------- completeness

    @Test
    void requireComplete_namesEveryShortRound() {
        pipelineOf(technical, culture);
        panelsOf(panel(technical, 3));
        seated(seat(technical, UUID.randomUUID()), seat(technical, UUID.randomUUID()));

        assertThatThrownBy(() -> service.requireComplete(vacancy, InterviewPanelService.NOT_READY_TO_PUBLISH))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy can't be published yet: Technical interview needs 3 interviewers, 2 chosen; "
                        + "Culture interview needs its interviewers.");
    }

    @Test
    void requireComplete_passesWhenEveryRoundHasExactlyItsCount() {
        pipelineOf(technical);
        panelsOf(panel(technical, 1));
        seated(seat(technical, UUID.randomUUID()));

        assertThatCode(() -> service.requireComplete(vacancy, InterviewPanelService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void requireComplete_passesAVacancyWithoutInterviewRounds() {
        pipelineOf();

        assertThatCode(() -> service.requireComplete(vacancy, InterviewPanelService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    @Test
    void requireComplete_forANewVacancy_readsTheTemplateAndTheRequest() {
        List<PipelineStageResponse> stages = List.of(
                new PipelineStageResponse(UUID.randomUUID(), 0, StageType.POST_APPLICATION, "Applied", null),
                new PipelineStageResponse(UUID.randomUUID(), 1, StageType.INTERVIEW, "Technical interview", 70),
                new PipelineStageResponse(UUID.randomUUID(), 2, StageType.ONBOARD, "Onboard", null));
        UUID ada = UUID.randomUUID();

        assertThatThrownBy(() -> InterviewPanelService.requireComplete(stages,
                List.of(new InterviewPanelRequest(1, 2, List.of(ada))), InterviewPanelService.NOT_READY_TO_PUBLISH))
                .hasMessage("This vacancy can't be published yet: Technical interview needs 2 interviewers, 1 chosen.");
        assertThatThrownBy(() -> InterviewPanelService.requireComplete(stages, null,
                InterviewPanelService.NOT_READY_TO_PUBLISH))
                .hasMessage("This vacancy can't be published yet: Technical interview needs its interviewers.");
        assertThatCode(() -> InterviewPanelService.requireComplete(stages,
                List.of(new InterviewPanelRequest(1, 1, List.of(ada))), InterviewPanelService.NOT_READY_TO_PUBLISH))
                .doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ read

    @Test
    void get_listsOnlyInterviewRounds_inOrder_withTheirPeople() {
        UUID ada = UUID.randomUUID();
        when(access.require(VIEWER, vacancy.getId())).thenReturn(vacancy);
        pipelineOf(stage(0, StageType.POST_APPLICATION, "Applied"), technical, culture,
                stage(4, StageType.ONBOARD, "Onboard"));
        panelsOf(panel(technical, 1));
        seated(seat(technical, ada));
        when(userDirectory.describe(any(), anyCollection())).thenReturn(Map.of(
                ada, new MemberSummary(ada, "Ada", "Lovelace", "ada@acme.io", "Interviewer", "ACTIVE")));

        List<InterviewPanelResponse> panels = service.get(VIEWER, vacancy.getId());

        assertThat(panels).extracting(InterviewPanelResponse::name)
                .containsExactly("Technical interview", "Culture interview");
        assertThat(panels.get(0).interviewerCount()).isEqualTo(1);
        assertThat(panels.get(0).interviewers()).extracting(MemberSummary::id).containsExactly(ada);
        assertThat(panels.get(1).interviewerCount()).isNull();
        assertThat(panels.get(1).interviewers()).isEmpty();
    }

    // -------------------------------------------------------------- fixtures

    private JobStage stage(int position, StageType type, String name) {
        JobStage stage = new JobStage(TENANT, vacancy.getId(), position, type, name,
                type == StageType.INTERVIEW ? 70 : null);
        ReflectionTestUtils.setField(stage, "id", UUID.randomUUID());
        return stage;
    }

    private void pipelineOf(JobStage... stages) {
        when(stageRepository.findAllByJobIdOrderByPositionAsc(vacancy.getId())).thenReturn(List.of(stages));
    }

    private JobStagePanel panel(JobStage stage, int count) {
        return new JobStagePanel(TENANT, vacancy.getId(), stage.getId(), count, null);
    }

    private void panelsOf(JobStagePanel... panels) {
        when(panelRepository.findAllByJobId(vacancy.getId())).thenReturn(List.of(panels));
    }

    private JobStageInterviewer seat(JobStage stage, UUID userId) {
        return new JobStageInterviewer(TENANT, vacancy.getId(), stage.getId(), userId, null);
    }

    private void seated(JobStageInterviewer... rows) {
        when(interviewerRepository.findAllByJobIdOrderByAddedAtAscUserIdAsc(vacancy.getId()))
                .thenReturn(List.of(rows));
    }
}
