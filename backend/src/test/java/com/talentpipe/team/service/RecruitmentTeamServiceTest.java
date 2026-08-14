package com.talentpipe.team.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.exception.DuplicateResourceException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.team.dto.*;
import com.talentpipe.team.entity.RecruitmentTeam;
import com.talentpipe.team.entity.RecruitmentTeamMember;
import com.talentpipe.team.entity.RecruitmentTeamVacancy;
import com.talentpipe.team.entity.TeamMemberRole;
import com.talentpipe.team.repository.RecruitmentTeamMemberRepository;
import com.talentpipe.team.repository.RecruitmentTeamRepository;
import com.talentpipe.team.repository.RecruitmentTeamVacancyRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecruitmentTeamServiceTest {

    @Mock
    private RecruitmentTeamRepository teamRepository;
    @Mock
    private RecruitmentTeamMemberRepository memberRepository;
    @Mock
    private RecruitmentTeamVacancyRepository vacancyRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private RecruitmentTeamService service;

    private UUID tenantId;
    private UUID teamId;
    private UUID userId;
    private UUID vacancyId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        teamId = UUID.randomUUID();
        userId = UUID.randomUUID();
        vacancyId = UUID.randomUUID();
    }

    @Test
    void createTeam_success() {
        CreateTeamRequest request = new CreateTeamRequest("Tech Talent Team", "Handles Engineering hiring", "Engineering", null);
        when(teamRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Tech Talent Team")).thenReturn(false);
        when(teamRepository.save(any(RecruitmentTeam.class))).thenAnswer(i -> i.getArgument(0));

        TeamResponse response = service.createTeam(tenantId, request);

        assertThat(response.name()).isEqualTo("Tech Talent Team");
        assertThat(response.department()).isEqualTo("Engineering");
        verify(teamRepository).save(any(RecruitmentTeam.class));
    }

    @Test
    void createTeam_duplicateName_throwsDuplicateResourceException() {
        CreateTeamRequest request = new CreateTeamRequest("Tech Talent Team", null, null, null);
        when(teamRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Tech Talent Team")).thenReturn(true);

        assertThatThrownBy(() -> service.createTeam(tenantId, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Tech Talent Team");

        verify(teamRepository, never()).save(any());
    }

    @Test
    void createTeam_invalidLeadId_throwsResourceNotFoundException() {
        UUID leadId = UUID.randomUUID();
        CreateTeamRequest request = new CreateTeamRequest("Tech Team", null, null, leadId);
        when(teamRepository.existsByTenantIdAndNameIgnoreCase(tenantId, "Tech Team")).thenReturn(false);
        when(userRepository.findById(leadId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTeam(tenantId, request))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTeams_filteredByDepartment() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Design Squad", "UI/UX", "Product", null);
        when(teamRepository.findAllByTenantIdAndDepartmentIgnoreCase(tenantId, "Product")).thenReturn(List.of(team));
        when(memberRepository.countByTenantIdAndTeamId(tenantId, team.getId())).thenReturn(2L);
        when(vacancyRepository.countByTenantIdAndTeamId(tenantId, team.getId())).thenReturn(1L);

        List<TeamResponse> responses = service.getTeams(tenantId, "Product");

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("Design Squad");
        assertThat(responses.get(0).memberCount()).isEqualTo(2);
        assertThat(responses.get(0).vacancyCount()).isEqualTo(1);
    }

    @Test
    void updateTeam_success() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Old Name", "Desc", "Engineering", null);
        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));
        when(teamRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, "New Name", teamId)).thenReturn(false);
        when(teamRepository.save(any(RecruitmentTeam.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTeamRequest request = new UpdateTeamRequest("New Name", "Updated Desc", null, null);
        TeamResponse response = service.updateTeam(tenantId, teamId, request);

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.description()).isEqualTo("Updated Desc");
    }

    @Test
    void deleteTeam_success() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Team To Delete", null, null, null);
        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));

        service.deleteTeam(tenantId, teamId);

        verify(memberRepository).deleteAllByTenantIdAndTeamId(tenantId, teamId);
        verify(vacancyRepository).deleteAllByTenantIdAndTeamId(tenantId, teamId);
        verify(teamRepository).deleteById(teamId);
    }

    @Test
    void addMember_success() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Recruiter Team", null, null, null);
        User user = mock(User.class);
        when(user.getTenantId()).thenReturn(tenantId);
        when(user.getEmail()).thenReturn("recruiter@talentpipe.io");
        when(user.getFirstName()).thenReturn("Jane");
        when(user.getLastName()).thenReturn("Doe");

        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(false);
        when(memberRepository.save(any(RecruitmentTeamMember.class))).thenAnswer(i -> i.getArgument(0));

        AddTeamMemberRequest request = new AddTeamMemberRequest(userId, TeamMemberRole.LEAD);
        TeamMemberResponse response = service.addMember(tenantId, teamId, request);

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.memberRole()).isEqualTo(TeamMemberRole.LEAD);
        assertThat(response.userName()).isEqualTo("Jane Doe");
    }

    @Test
    void addMember_duplicateMember_throwsDuplicateResourceException() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Recruiter Team", null, null, null);
        User user = mock(User.class);
        when(user.getTenantId()).thenReturn(tenantId);

        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(memberRepository.existsByTeamIdAndUserId(teamId, userId)).thenReturn(true);

        AddTeamMemberRequest request = new AddTeamMemberRequest(userId, TeamMemberRole.RECRUITER);
        assertThatThrownBy(() -> service.addMember(tenantId, teamId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void mapVacancy_success() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Team", null, null, null);
        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));
        when(vacancyRepository.existsByTeamIdAndVacancyId(teamId, vacancyId)).thenReturn(false);
        when(vacancyRepository.save(any(RecruitmentTeamVacancy.class))).thenAnswer(i -> i.getArgument(0));

        MapVacancyRequest request = new MapVacancyRequest(vacancyId);
        TeamVacancyResponse response = service.mapVacancy(tenantId, teamId, request);

        assertThat(response.vacancyId()).isEqualTo(vacancyId);
        assertThat(response.teamId()).isEqualTo(teamId);
    }

    @Test
    void unmapVacancy_success() {
        RecruitmentTeam team = new RecruitmentTeam(tenantId, "Team", null, null, null);
        RecruitmentTeamVacancy vacancyMap = new RecruitmentTeamVacancy(tenantId, teamId, vacancyId);

        when(teamRepository.findByTenantIdAndId(tenantId, teamId)).thenReturn(Optional.of(team));
        when(vacancyRepository.findByTenantIdAndTeamIdAndVacancyId(tenantId, teamId, vacancyId))
                .thenReturn(Optional.of(vacancyMap));

        service.unmapVacancy(tenantId, teamId, vacancyId);

        verify(vacancyRepository).deleteByTenantIdAndTeamIdAndVacancyId(tenantId, teamId, vacancyId);
    }
}
