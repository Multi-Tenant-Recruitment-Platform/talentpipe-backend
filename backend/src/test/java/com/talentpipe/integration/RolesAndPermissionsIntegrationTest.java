package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.security.Permission;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for company-defined roles through the real API against a
 * real PostgreSQL: the default roles every workspace gets, building a custom
 * role out of tasks, giving it to someone, and the guardrails — permissions
 * take effect on the very next request, nobody can hand out more than they
 * hold, a workspace always keeps an Owner, and another company's roles are
 * invisible.
 */
@SuppressWarnings("unchecked")
class RolesAndPermissionsIntegrationTest extends AbstractWorkspaceIntegrationTest {

    // ------------------------------------------------------------- defaults

    @Test
    void aNewWorkspace_hasOwnerRecruiterAndInterviewer_andItsFounderIsTheOwner() {
        Workspace acme = newWorkspace("Acme");

        List<Map<String, Object>> roles = callForList("/api/v1/roles", acme.adminToken()).getBody();
        assertThat(roles).extracting(role -> role.get("name"))
                .containsExactly("Owner", "Recruiter", "Interviewer");
        assertThat(roles.get(0)).containsEntry("systemRole", "OWNER")
                .containsEntry("locked", true)
                .containsEntry("memberCount", 1);
        assertThat(roles.get(1)).containsEntry("locked", false).containsEntry("deletable", false);

        Map<String, Object> me = call(HttpMethod.GET, "/api/v1/auth/me", acme.adminToken(), null).getBody();
        assertThat(me).containsEntry("role", "COMPANY_ADMIN").containsEntry("roleName", "Owner");
        assertThat((List<Object>) me.get("permissions")).hasSize(Permission.values().length);
    }

    @Test
    void theLoginResponse_carriesTheRoleAndWhatItAllows() {
        Workspace acme = newWorkspace("Acme");
        String hr = newMember(acme, "HR_MANAGER");

        Map<String, Object> user = call(HttpMethod.GET, "/api/v1/auth/me", hr, null).getBody();

        assertThat(user).containsEntry("role", "HR_MANAGER").containsEntry("roleName", "Recruiter");
        assertThat((List<String>) user.get("permissions"))
                .contains("jobs.manage", "jobs.publish")
                .doesNotContain("team.view", "settings.edit");
    }

    @Test
    void theCatalogue_isAvailableToTheRoleEditor() {
        Workspace acme = newWorkspace("Acme");

        List<Map<String, Object>> groups = callForList("/api/v1/permissions", acme.adminToken()).getBody();

        assertThat(groups).extracting(group -> group.get("key"))
                .containsExactly("TEAM", "COMPANY", "VACANCIES", "HIRING", "INTERVIEWS");
        int count = groups.stream().mapToInt(group -> ((List<?>) group.get("permissions")).size()).sum();
        assertThat(count).isEqualTo(Permission.values().length);
    }

    // ------------------------------------------------- the user's own example

    @Test
    void aCompanyCanBuildItsOwnRoles_andEachMemberGetsExactlyWhatTheirRoleAllows() {
        Workspace acme = newWorkspace("Acme");

        // Role A: runs the team and the interview process.
        Map<String, Object> coordinator = createRole(acme.adminToken(), "Hiring Coordinator",
                List.of("team.invite", "team.manage", "interviews.schedule"));
        assertThat((List<String>) coordinator.get("permissions"))
                // team.view comes along: inviting people means seeing the team.
                .containsExactly("team.view", "team.invite", "team.manage", "interviews.schedule");
        // Role B: only conducts the interviews (e.g. over Google Meet).
        Map<String, Object> panel = createRole(acme.adminToken(), "Panel Interviewer",
                List.of("interviews.conduct"));

        Member alice = newMemberWithRole(acme, UUID.fromString((String) coordinator.get("id")));
        Member bob = newMemberWithRole(acme, UUID.fromString((String) panel.get("id")));

        Map<String, Object> aliceMe = call(HttpMethod.GET, "/api/v1/auth/me", alice.token(), null).getBody();
        assertThat(aliceMe).containsEntry("roleName", "Hiring Coordinator").containsEntry("role", "CUSTOM");

        // Alice can see and grow the team...
        assertThat(statusOf(HttpMethod.GET, "/api/v1/team", alice.token()))
                .isEqualTo(HttpStatus.OK);
        // ...but cannot edit the company profile or manage vacancies.
        assertThat(call(HttpMethod.PATCH, "/api/v1/tenant", alice.token(),
                Map.of("name", "Hijacked", "email", "x@y.io")).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/v1/jobs", alice.token(), Map.of("openings", 1, "status", "DRAFT"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Bob can do none of that, but is still a member of the workspace.
        assertThat(statusOf(HttpMethod.GET, "/api/v1/team", bob.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.GET, "/api/v1/tenant", bob.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void removingAPermission_stopsItWorkingOnTheVeryNextRequest() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> role = createRole(acme.adminToken(), "Team Lead", List.of("team.view"));
        Member lead = newMemberWithRole(acme, UUID.fromString((String) role.get("id")));
        assertThat(statusOf(HttpMethod.GET, "/api/v1/team", lead.token()))
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<Map<String, Object>> edited = call(HttpMethod.PUT, "/api/v1/roles/" + role.get("id"),
                acme.adminToken(), Map.of("name", "Team Lead", "permissions", List.of(), "version", role.get("version")));
        assertThat(edited.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Same access token, no re-login: the change applies at once.
        assertThat(statusOf(HttpMethod.GET, "/api/v1/team", lead.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void movingSomeoneToAnotherRole_alsoAppliesAtOnce() {
        Workspace acme = newWorkspace("Acme");
        Member member = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        assertThat(call(HttpMethod.POST, "/api/v1/jobs", member.token(), Map.of("openings", 1, "status", "DRAFT"))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> moved = call(HttpMethod.PUT, "/api/v1/team/members/" + member.id() + "/role",
                acme.adminToken(), Map.of("roleId", roleId(acme, "Recruiter").toString()));

        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(moved.getBody()).containsEntry("roleName", "Recruiter").containsEntry("role", "HR_MANAGER");
        assertThat(call(HttpMethod.POST, "/api/v1/jobs", member.token(), Map.of("openings", 1, "status", "DRAFT"))
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    // -------------------------------------------------------------- guardrails

    @Test
    void nobodyCanHandOutMoreThanTheyHold() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> roleManager = createRole(acme.adminToken(), "Role Manager",
                List.of("roles.manage", "team.invite"));
        Member manager = newMemberWithRole(acme, UUID.fromString((String) roleManager.get("id")));

        // Creating a role with a permission they lack...
        ResponseEntity<Map<String, Object>> escalation = call(HttpMethod.POST, "/api/v1/roles", manager.token(),
                Map.of("name", "Publisher", "permissions", List.of("jobs.publish")));
        assertThat(escalation.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) escalation.getBody().get("message"))
                .startsWith("You can only give out permissions you have yourself.");

        // ...or inviting someone into a role that has one, is refused.
        ResponseEntity<Map<String, Object>> invite = call(HttpMethod.POST, "/api/v1/team/invitations", manager.token(),
                Map.of("firstName", "E", "lastName", "Ve", "email", "eve@acme.io",
                        "roleId", roleId(acme, "Recruiter").toString()));
        assertThat(invite.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void theLastOwner_cannotBeMoved_andOnlyAnOwnerCanMakeAnotherOwner() {
        Workspace acme = newWorkspace("Acme");
        UUID founder = userIdWithRole(acme, "COMPANY_ADMIN");

        ResponseEntity<Map<String, Object>> demote = call(HttpMethod.PUT, "/api/v1/team/members/" + founder + "/role",
                acme.adminToken(), Map.of("roleId", roleId(acme, "Recruiter").toString()));
        assertThat(demote.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) demote.getBody().get("message")).startsWith("Every workspace needs at least one Owner.");

        // Invitations never create an Owner...
        ResponseEntity<Map<String, Object>> inviteOwner = call(HttpMethod.POST, "/api/v1/team/invitations",
                acme.adminToken(), Map.of("firstName", "C", "lastName", "O", "email", "co@acme.io",
                        "roleId", roleId(acme, "Owner").toString()));
        assertThat(inviteOwner.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // ...but an Owner can promote a member, after which the founder may step down.
        Member cofounder = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        assertThat(call(HttpMethod.PUT, "/api/v1/team/members/" + cofounder.id() + "/role", acme.adminToken(),
                Map.of("roleId", roleId(acme, "Owner").toString())).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PUT, "/api/v1/team/members/" + founder + "/role", cofounder.token(),
                Map.of("roleId", roleId(acme, "Recruiter").toString())).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void theOwnerRole_cannotBeEditedOrDeleted_andDefaultRolesCannotBeDeleted() {
        Workspace acme = newWorkspace("Acme");
        UUID owner = roleId(acme, "Owner");

        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + owner, acme.adminToken(),
                Map.of("name", "Boss", "permissions", List.of(), "version", 0)).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(call(HttpMethod.DELETE, "/api/v1/roles/" + owner, acme.adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(call(HttpMethod.DELETE, "/api/v1/roles/" + roleId(acme, "Interviewer"), acme.adminToken(), null)
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void deletingARoleInUse_needsAReassignment_thenMovesItsMembers() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> role = createRole(acme.adminToken(), "Temp", List.of("team.view"));
        Member member = newMemberWithRole(acme, UUID.fromString((String) role.get("id")));

        ResponseEntity<Map<String, Object>> refused =
                call(HttpMethod.DELETE, "/api/v1/roles/" + role.get("id"), acme.adminToken(), null);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody()).containsEntry("message",
                "1 person has this role. Choose a role to move them to before deleting it.");

        UUID interviewer = roleId(acme, "Interviewer");
        assertThat(call(HttpMethod.DELETE, "/api/v1/roles/" + role.get("id") + "?reassignTo=" + interviewer,
                acme.adminToken(), null).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(call(HttpMethod.GET, "/api/v1/auth/me", member.token(), null).getBody())
                .containsEntry("roleName", "Interviewer");
        assertThat(callForList("/api/v1/roles", acme.adminToken()).getBody())
                .extracting(r -> r.get("name")).doesNotContain("Temp");
    }

    @Test
    void roleNames_areUniqueIgnoringCase_andEditsAreOptimisticallyLocked() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> role = createRole(acme.adminToken(), "Sourcer", List.of("team.view"));

        assertThat(call(HttpMethod.POST, "/api/v1/roles", acme.adminToken(),
                Map.of("name", "SOURCER", "permissions", List.of())).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        Map<String, Object> first = call(HttpMethod.PUT, "/api/v1/roles/" + role.get("id"), acme.adminToken(),
                Map.of("name", "Sourcer", "permissions", List.of("team.view", "team.invite"), "version", 0)).getBody();
        assertThat(first).containsEntry("version", 1);
        ResponseEntity<Map<String, Object>> stale = call(HttpMethod.PUT, "/api/v1/roles/" + role.get("id"),
                acme.adminToken(), Map.of("name", "Sourcer", "permissions", List.of(), "version", 0));
        assertThat(stale.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(stale.getBody()).containsEntry("code", "CONCURRENT_UPDATE");
    }

    @Test
    void duplicate_startsACustomRoleFromAnExistingOne() {
        Workspace acme = newWorkspace("Acme");

        ResponseEntity<Map<String, Object>> copy = call(HttpMethod.POST,
                "/api/v1/roles/" + roleId(acme, "Recruiter") + "/duplicate", acme.adminToken(), null);

        assertThat(copy.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(copy.getBody()).containsEntry("name", "Recruiter (copy)").containsEntry("systemRole", null);
        assertThat((List<String>) copy.getBody().get("permissions")).contains("jobs.publish");
    }

    @Test
    void anotherCompanysRoles_areInvisible() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        UUID acmeRecruiter = roleId(acme, "Recruiter");

        assertThat(call(HttpMethod.GET, "/api/v1/roles/" + acmeRecruiter, globex.adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PUT, "/api/v1/roles/" + acmeRecruiter, globex.adminToken(),
                Map.of("name", "x", "permissions", List.of(), "version", 0)).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.POST, "/api/v1/team/invitations", globex.adminToken(),
                Map.of("firstName", "A", "lastName", "B", "email", "spy@globex.io",
                        "roleId", acmeRecruiter.toString())).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(callForList("/api/v1/roles", globex.adminToken()).getBody()).hasSize(3);
    }

    // ------------------------------------------------------------ team & audit

    @Test
    void theTeamListing_showsRolesAndWhenInvitationsExpire() {
        Workspace acme = newWorkspace("Acme");
        call(HttpMethod.POST, "/api/v1/team/invitations", acme.adminToken(), Map.of("firstName", "P", "lastName", "Q",
                "email", "pending@acme.io", "roleId", roleId(acme, "Interviewer").toString()));

        List<Map<String, Object>> team = callForList("/api/v1/team", acme.adminToken()).getBody();

        Map<String, Object> pending = team.stream().filter(m -> "pending@acme.io".equals(m.get("email")))
                .findFirst().orElseThrow();
        assertThat(pending).containsEntry("status", "INVITED").containsEntry("roleName", "Interviewer");
        assertThat(pending.get("invitationSentAt")).isNotNull();
        assertThat(pending.get("invitationExpiresAt")).isNotNull();
        Map<String, Object> founder = team.stream().filter(m -> "ACTIVE".equals(m.get("status")))
                .findFirst().orElseThrow();
        assertThat(founder).containsEntry("roleName", "Owner").containsEntry("invitationSentAt", null);
    }

    @Test
    void everyRoleChange_isAudited_withWhoAndWhat() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> role = createRole(acme.adminToken(), "Scheduler", List.of("interviews.schedule"));
        Member member = newMemberWithRole(acme, UUID.fromString((String) role.get("id")));
        call(HttpMethod.PUT, "/api/v1/team/members/" + member.id() + "/role", acme.adminToken(),
                Map.of("roleId", roleId(acme, "Interviewer").toString()));

        List<Map<String, Object>> events = (List<Map<String, Object>>)
                call(HttpMethod.GET, "/api/v1/roles/audit", acme.adminToken(), null).getBody().get("content");

        assertThat(events).extracting(event -> event.get("action"))
                .containsExactly("MEMBER_ROLE_CHANGED", "ROLE_CREATED");
        assertThat(events.get(0)).containsEntry("previousValue", "Scheduler").containsEntry("newValue", "Interviewer")
                .containsEntry("targetUserId", member.id().toString());
        assertThat(events.get(1)).containsEntry("newValue", "Scheduler: interviews.schedule");
    }

    @Test
    void theOldWayOfInviting_byRoleName_stillWorks() {
        Workspace acme = newWorkspace("Acme");

        String hr = newMember(acme, "HR_MANAGER");

        assertThat(call(HttpMethod.GET, "/api/v1/auth/me", hr, null).getBody()).containsEntry("roleName", "Recruiter");
    }

    // ----------------------------------------------------------------- helpers

    private Map<String, Object> createRole(String token, String name, List<String> permissions) {
        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/v1/roles", token,
                Map.of("name", name, "permissions", permissions));
        assertThat(created.getStatusCode()).as("create role: %s", created.getBody()).isEqualTo(HttpStatus.CREATED);
        return created.getBody();
    }
}
