package com.talentpipe.job.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.talentpipe.auth.dto.MemberSummary;
import com.talentpipe.auth.service.UserDirectoryService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.job.VacancyFixtures;
import com.talentpipe.job.dto.HiringTeamRequest;
import com.talentpipe.job.dto.HiringTeamResponse;
import com.talentpipe.job.entity.JobVacancy;
import com.talentpipe.job.entity.VacancyTeamMember;
import com.talentpipe.job.repository.VacancyTeamMemberRepository;
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

/** Unit tests for a vacancy's hiring team: reading it, replacing it, copying it to a duplicate. */
@ExtendWith(MockitoExtension.class)
class VacancyHiringTeamServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final VacancyViewer VIEWER = new VacancyViewer(TENANT, ACTOR, true);

    @Mock
    private VacancyAccess access;
    @Mock
    private VacancyTeamMemberRepository teamRepository;
    @Mock
    private UserDirectoryService userDirectory;
    @Captor
    private ArgumentCaptor<Collection<VacancyTeamMember>> removed;
    @Captor
    private ArgumentCaptor<VacancyTeamMember> added;

    private VacancyHiringTeamService service;

    @BeforeEach
    void setUp() {
        service = new VacancyHiringTeamService(access, teamRepository, userDirectory);
    }

    @Test
    void get_listsTheRecruiterAndManagerSeparatelyFromTheTeam() {
        UUID recruiter = UUID.randomUUID();
        UUID interviewer = UUID.randomUUID();
        JobVacancy vacancy = visible(VacancyFixtures.persisted(new JobVacancy(TENANT,
                VacancyFixtures.complete().assignedRecruiterId(recruiter).hiringManagerId(null).build())));
        when(teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(vacancy.getId()))
                .thenReturn(List.of(member(vacancy, interviewer)));
        when(userDirectory.describe(TENANT, List.of(interviewer, recruiter))).thenReturn(Map.of(
                recruiter, person(recruiter, "Recruiter"),
                interviewer, person(interviewer, "Interviewer")));

        HiringTeamResponse team = service.get(VIEWER, vacancy.getId());

        assertThat(team.recruiter().id()).isEqualTo(recruiter);
        assertThat(team.hiringManager()).isNull();
        assertThat(team.members()).extracting(MemberSummary::id).containsExactly(interviewer);
    }

    @Test
    void replace_addsNewcomers_removesTheDropped_andKeepsTheRest() {
        JobVacancy vacancy = visible(VacancyFixtures.draft(TENANT));
        UUID staying = UUID.randomUUID();
        UUID leaving = UUID.randomUUID();
        UUID joining = UUID.randomUUID();
        when(teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(vacancy.getId()))
                .thenReturn(List.of(member(vacancy, staying), member(vacancy, leaving)));
        when(userDirectory.activeMembersAmong(TENANT, Set.of(joining))).thenReturn(Set.of(joining));

        service.replace(VIEWER, vacancy.getId(), new HiringTeamRequest(List.of(staying, joining, joining)));

        verify(teamRepository).deleteAll(removed.capture());
        assertThat(removed.getValue()).extracting(VacancyTeamMember::getUserId).containsExactly(leaving);
        verify(teamRepository).save(added.capture());
        assertThat(added.getValue().getUserId()).isEqualTo(joining);
        assertThat(added.getValue().getTenantId()).isEqualTo(TENANT);
    }

    @Test
    void replace_refusesSomeoneWhoIsNotAnActiveMember_andChangesNothing() {
        JobVacancy vacancy = visible(VacancyFixtures.draft(TENANT));
        UUID outsider = UUID.randomUUID();
        when(userDirectory.activeMembersAmong(TENANT, Set.of(outsider))).thenReturn(Set.of());
        UUID vacancyId = vacancy.getId();
        HiringTeamRequest request = new HiringTeamRequest(List.of(outsider));

        assertThatThrownBy(() -> service.replace(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("active member");
        verify(teamRepository, never()).save(any());
        verify(teamRepository, never()).deleteAll(anyCollection());
    }

    @Test
    void replace_onAnArchivedVacancy_isRefused() {
        JobVacancy vacancy = visible(VacancyFixtures.archived(TENANT));
        UUID vacancyId = vacancy.getId();
        HiringTeamRequest request = new HiringTeamRequest(List.of());

        assertThatThrownBy(() -> service.replace(VIEWER, vacancyId, request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("archived");
    }

    @Test
    void copyTeam_carriesOverOnlyPeopleWhoAreStillActive() {
        JobVacancy source = VacancyFixtures.draft(TENANT);
        UUID copyId = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID departed = UUID.randomUUID();
        when(teamRepository.findAllByVacancyIdOrderByAddedAtAscUserIdAsc(source.getId()))
                .thenReturn(List.of(member(source, active), member(source, departed)));
        when(userDirectory.activeMembersAmong(TENANT, List.of(active, departed))).thenReturn(Set.of(active));

        service.copyTeam(TENANT, source.getId(), copyId, ACTOR);

        verify(teamRepository).save(added.capture());
        assertThat(added.getValue().getVacancyId()).isEqualTo(copyId);
        assertThat(added.getValue().getUserId()).isEqualTo(active);
    }

    // ---------------------------------------------------------------- helpers

    private JobVacancy visible(JobVacancy vacancy) {
        when(access.require(VIEWER, vacancy.getId())).thenReturn(vacancy);
        return vacancy;
    }

    private static VacancyTeamMember member(JobVacancy vacancy, UUID userId) {
        return new VacancyTeamMember(vacancy.getId(), userId, TENANT, ACTOR);
    }

    private static MemberSummary person(UUID id, String roleName) {
        return new MemberSummary(id, "Ada", "Lovelace", "ada@acme.io", roleName, "ACTIVE");
    }
}
