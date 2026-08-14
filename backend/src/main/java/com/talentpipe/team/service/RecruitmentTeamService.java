package com.talentpipe.team.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class RecruitmentTeamService {

    private final RecruitmentTeamRepository teamRepository;
    private final RecruitmentTeamMemberRepository memberRepository;
    private final RecruitmentTeamVacancyRepository vacancyRepository;
    private final UserRepository userRepository;

    public RecruitmentTeamService(
            RecruitmentTeamRepository teamRepository,
            RecruitmentTeamMemberRepository memberRepository,
            RecruitmentTeamVacancyRepository vacancyRepository,
            UserRepository userRepository) {
        this.teamRepository = teamRepository;
        this.memberRepository = memberRepository;
        this.vacancyRepository = vacancyRepository;
        this.userRepository = userRepository;
    }

    // ---------------------------------------------------------------- TEAM CRUD

    public TeamResponse createTeam(UUID tenantId, CreateTeamRequest request) {
        if (teamRepository.existsByTenantIdAndNameIgnoreCase(tenantId, request.name())) {
            throw new DuplicateResourceException("Recruitment team with name '" + request.name() + "' already exists");
        }

        if (request.leadId() != null) {
            validateTenantUser(tenantId, request.leadId());
        }

        RecruitmentTeam team = new RecruitmentTeam(
                tenantId,
                request.name().trim(),
                request.description(),
                request.department() != null ? request.department().trim() : null,
                request.leadId()
        );

        RecruitmentTeam saved = teamRepository.save(team);
        return toTeamResponse(saved, 0, 0);
    }

    @Transactional(readOnly = true)
    public List<TeamResponse> getTeams(UUID tenantId, String department) {
        List<RecruitmentTeam> teams;
        if (department != null && !department.isBlank()) {
            teams = teamRepository.findAllByTenantIdAndDepartmentIgnoreCase(tenantId, department.trim());
        } else {
            teams = teamRepository.findAllByTenantId(tenantId);
        }

        return teams.stream().map(team -> {
            long memberCount = memberRepository.countByTenantIdAndTeamId(tenantId, team.getId());
            long vacancyCount = vacancyRepository.countByTenantIdAndTeamId(tenantId, team.getId());
            return toTeamResponse(team, memberCount, vacancyCount);
        }).toList();
    }

    @Transactional(readOnly = true)
    public TeamDetailResponse getTeamDetail(UUID tenantId, UUID teamId) {
        RecruitmentTeam team = findTeamOrThrow(tenantId, teamId);

        List<RecruitmentTeamMember> members = memberRepository.findAllByTenantIdAndTeamId(tenantId, teamId);
        List<RecruitmentTeamVacancy> vacancies = vacancyRepository.findAllByTenantIdAndTeamId(tenantId, teamId);

        List<UUID> userIds = members.stream().map(RecruitmentTeamMember::getUserId).toList();
        Map<UUID, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        List<TeamMemberResponse> memberResponses = members.stream().map(m -> {
            User u = userMap.get(m.getUserId());
            String email = u != null ? u.getEmail() : null;
            String name = u != null ? u.getFirstName() + " " + u.getLastName() : null;
            return new TeamMemberResponse(m.getId(), m.getTeamId(), m.getUserId(), email, name, m.getMemberRole(), m.getCreatedAt());
        }).toList();

        List<TeamVacancyResponse> vacancyResponses = vacancies.stream()
                .map(v -> new TeamVacancyResponse(v.getId(), v.getTeamId(), v.getVacancyId(), v.getCreatedAt()))
                .toList();

        String leadName = getLeadName(team.getLeadId());

        return new TeamDetailResponse(
                team.getId(),
                team.getTenantId(),
                team.getName(),
                team.getDescription(),
                team.getDepartment(),
                team.getLeadId(),
                leadName,
                memberResponses,
                vacancyResponses,
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }

    public TeamResponse updateTeam(UUID tenantId, UUID teamId, UpdateTeamRequest request) {
        RecruitmentTeam team = findTeamOrThrow(tenantId, teamId);

        if (request.name() != null && !request.name().isBlank()) {
            String newName = request.name().trim();
            if (teamRepository.existsByTenantIdAndNameIgnoreCaseAndIdNot(tenantId, newName, teamId)) {
                throw new DuplicateResourceException("Recruitment team with name '" + newName + "' already exists");
            }
            team.setName(newName);
        }

        if (request.description() != null) {
            team.setDescription(request.description());
        }

        if (request.department() != null) {
            team.setDepartment(request.department().isBlank() ? null : request.department().trim());
        }

        if (request.leadId() != null) {
            validateTenantUser(tenantId, request.leadId());
            team.setLeadId(request.leadId());
        }

        RecruitmentTeam updated = teamRepository.save(team);
        long memberCount = memberRepository.countByTenantIdAndTeamId(tenantId, teamId);
        long vacancyCount = vacancyRepository.countByTenantIdAndTeamId(tenantId, teamId);

        return toTeamResponse(updated, memberCount, vacancyCount);
    }

    public void deleteTeam(UUID tenantId, UUID teamId) {
        findTeamOrThrow(tenantId, teamId);
        memberRepository.deleteAllByTenantIdAndTeamId(tenantId, teamId);
        vacancyRepository.deleteAllByTenantIdAndTeamId(tenantId, teamId);
        teamRepository.deleteById(teamId);
    }

    // -------------------------------------------------------- TEAM MEMBERSHIP

    public TeamMemberResponse addMember(UUID tenantId, UUID teamId, AddTeamMemberRequest request) {
        findTeamOrThrow(tenantId, teamId);
        User user = validateTenantUser(tenantId, request.userId());

        if (memberRepository.existsByTeamIdAndUserId(teamId, request.userId())) {
            throw new DuplicateResourceException("User is already a member of this recruitment team");
        }

        TeamMemberRole role = request.memberRole() != null ? request.memberRole() : TeamMemberRole.RECRUITER;
        RecruitmentTeamMember member = new RecruitmentTeamMember(tenantId, teamId, request.userId(), role);
        RecruitmentTeamMember saved = memberRepository.save(member);

        String name = user.getFirstName() + " " + user.getLastName();
        return new TeamMemberResponse(saved.getId(), saved.getTeamId(), saved.getUserId(), user.getEmail(), name, saved.getMemberRole(), saved.getCreatedAt());
    }

    public TeamMemberResponse updateMemberRole(UUID tenantId, UUID teamId, UUID userId, UpdateTeamMemberRoleRequest request) {
        RecruitmentTeamMember member = memberRepository.findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found for team " + teamId + " and user " + userId));

        member.setMemberRole(request.memberRole());
        RecruitmentTeamMember saved = memberRepository.save(member);

        User user = userRepository.findById(userId).orElse(null);
        String name = user != null ? user.getFirstName() + " " + user.getLastName() : null;
        String email = user != null ? user.getEmail() : null;

        return new TeamMemberResponse(saved.getId(), saved.getTeamId(), saved.getUserId(), email, name, saved.getMemberRole(), saved.getCreatedAt());
    }

    public void removeMember(UUID tenantId, UUID teamId, UUID userId) {
        findTeamOrThrow(tenantId, teamId);
        memberRepository.findByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Team member not found for team " + teamId + " and user " + userId));
        memberRepository.deleteByTenantIdAndTeamIdAndUserId(tenantId, teamId, userId);
    }

    // ----------------------------------------------------- VACANCY MAPPING

    public TeamVacancyResponse mapVacancy(UUID tenantId, UUID teamId, MapVacancyRequest request) {
        findTeamOrThrow(tenantId, teamId);

        if (vacancyRepository.existsByTeamIdAndVacancyId(teamId, request.vacancyId())) {
            throw new DuplicateResourceException("Vacancy " + request.vacancyId() + " is already mapped to this team");
        }

        RecruitmentTeamVacancy vacancyMap = new RecruitmentTeamVacancy(tenantId, teamId, request.vacancyId());
        RecruitmentTeamVacancy saved = vacancyRepository.save(vacancyMap);

        return new TeamVacancyResponse(saved.getId(), saved.getTeamId(), saved.getVacancyId(), saved.getCreatedAt());
    }

    public void unmapVacancy(UUID tenantId, UUID teamId, UUID vacancyId) {
        findTeamOrThrow(tenantId, teamId);
        vacancyRepository.findByTenantIdAndTeamIdAndVacancyId(tenantId, teamId, vacancyId)
                .orElseThrow(() -> new ResourceNotFoundException("Vacancy mapping not found for team " + teamId + " and vacancy " + vacancyId));
        vacancyRepository.deleteByTenantIdAndTeamIdAndVacancyId(tenantId, teamId, vacancyId);
    }

    // ------------------------------------------------------------ HELPERS

    private RecruitmentTeam findTeamOrThrow(UUID tenantId, UUID teamId) {
        return teamRepository.findByTenantIdAndId(tenantId, teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Recruitment team not found with id: " + teamId));
    }

    private User validateTenantUser(UUID tenantId, UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        if (user.getTenantId() == null || !user.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException("User not found in tenant: " + userId);
        }
        return user;
    }

    private String getLeadName(UUID leadId) {
        if (leadId == null) {
            return null;
        }
        return userRepository.findById(leadId)
                .map(u -> u.getFirstName() + " " + u.getLastName())
                .orElse(null);
    }

    private TeamResponse toTeamResponse(RecruitmentTeam team, long memberCount, long vacancyCount) {
        String leadName = getLeadName(team.getLeadId());
        return new TeamResponse(
                team.getId(),
                team.getTenantId(),
                team.getName(),
                team.getDescription(),
                team.getDepartment(),
                team.getLeadId(),
                leadName,
                memberCount,
                vacancyCount,
                team.getCreatedAt(),
                team.getUpdatedAt()
        );
    }
}
