package com.talentpipe.team.controller;

import com.talentpipe.security.UserPrincipal;
import com.talentpipe.team.dto.*;
import com.talentpipe.team.service.RecruitmentTeamService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for Recruitment Team Management endpoints.
 */
@RestController
@RequestMapping("/api/v1/teams")
public class RecruitmentTeamController {

    private final RecruitmentTeamService teamService;

    public RecruitmentTeamController(RecruitmentTeamService teamService) {
        this.teamService = teamService;
    }

    // ---------------------------------------------------------------- TEAM CRUD

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse createTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTeamRequest request) {
        return teamService.createTeam(principal.tenantId(), request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'INTERVIEWER')")
    public List<TeamResponse> getTeams(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(name = "department", required = false) String department) {
        return teamService.getTeams(principal.tenantId(), department);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER', 'INTERVIEWER')")
    public TeamDetailResponse getTeamDetail(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId) {
        return teamService.getTeamDetail(principal.tenantId(), teamId);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    public TeamResponse updateTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @Valid @RequestBody UpdateTeamRequest request) {
        return teamService.updateTeam(principal.tenantId(), teamId, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId) {
        teamService.deleteTeam(principal.tenantId(), teamId);
    }

    // -------------------------------------------------------- TEAM MEMBERSHIP

    @PostMapping("/{id}/members")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse addMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @Valid @RequestBody AddTeamMemberRequest request) {
        return teamService.addMember(principal.tenantId(), teamId, request);
    }

    @PatchMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    public TeamMemberResponse updateMemberRole(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateTeamMemberRoleRequest request) {
        return teamService.updateMemberRole(principal.tenantId(), teamId, userId, request);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @PathVariable("userId") UUID userId) {
        teamService.removeMember(principal.tenantId(), teamId, userId);
    }

    // ----------------------------------------------------- VACANCY MAPPING

    @PostMapping("/{id}/vacancies")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamVacancyResponse mapVacancy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @Valid @RequestBody MapVacancyRequest request) {
        return teamService.mapVacancy(principal.tenantId(), teamId, request);
    }

    @DeleteMapping("/{id}/vacancies/{vacancyId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_MANAGER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unmapVacancy(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("id") UUID teamId,
            @PathVariable("vacancyId") UUID vacancyId) {
        teamService.unmapVacancy(principal.tenantId(), teamId, vacancyId);
    }
}
