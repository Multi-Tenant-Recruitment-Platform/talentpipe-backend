package com.talentpipe.auth.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asMemberWith;
import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.auth.dto.InviteUserRequest;
import com.talentpipe.auth.dto.TeamMemberResponse;
import com.talentpipe.auth.service.InvitationService;
import com.talentpipe.security.Permission;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** {@code @WebMvcTest} slice for {@link TeamController}: one permission per kind of team action. */
@WebMvcTest(TeamController.class)
@Import(WebMvcSecurityTestConfig.class)
class TeamControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final String INVITE =
            "{\"firstName\":\"Bob\",\"lastName\":\"Smith\",\"email\":\"bob@acme.io\",\"role\":\"INTERVIEWER\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvitationService invitationService;

    private final TeamMemberResponse bob = new TeamMemberResponse(MEMBER, TENANT, "Acme", "INTERVIEWER",
            UUID.randomUUID(), "Interviewer", "bob@acme.io", "Bob", "Smith", "INVITED", Instant.now(),
            Instant.now(), Instant.now().plusSeconds(3600));

    @Test
    void owner_canDoEverything() throws Exception {
        when(invitationService.listTeam(TENANT)).thenReturn(List.of(bob));

        mockMvc.perform(get("/api/v1/team").with(asUser(USER, TENANT, "COMPANY_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].roleName").value("Interviewer"))
                .andExpect(jsonPath("$[0].invitationExpiresAt").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"HR_MANAGER", "INTERVIEWER", "CANDIDATE"})
    void theDefaultNonOwnerRoles_haveNoTeamAccess_asBefore(String role) throws Exception {
        mockMvc.perform(get("/api/v1/team").with(asUser(USER, TENANT, role))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/team/invitations").with(asUser(USER, TENANT, role))
                        .contentType(MediaType.APPLICATION_JSON).content(INVITE))
                .andExpect(status().isForbidden());

        verifyNoInteractions(invitationService);
    }

    @Test
    void aCustomRole_getsExactlyTheTeamActionsItHolds() throws Exception {
        var coordinator = asMemberWith(USER, TENANT, Permission.TEAM_VIEW, Permission.TEAM_INVITE);
        when(invitationService.invite(eq(TENANT), eq(USER), any(InviteUserRequest.class))).thenReturn(bob);

        mockMvc.perform(get("/api/v1/team").with(coordinator)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/team/invitations").with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content(INVITE))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/team/invitations/{id}/resend", MEMBER).with(coordinator))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/team/invitations/{id}", MEMBER).with(coordinator))
                .andExpect(status().isNoContent());
        // ...but not changing anyone's role, which is team.manage.
        mockMvc.perform(put("/api/v1/team/members/{id}/role", MEMBER).with(coordinator)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void changeRole_needsTeamManage_andARole() throws Exception {
        UUID roleId = UUID.randomUUID();
        var manager = asMemberWith(USER, TENANT, Permission.TEAM_VIEW, Permission.TEAM_MANAGE);
        when(invitationService.changeRole(TENANT, USER, MEMBER, roleId)).thenReturn(bob);

        mockMvc.perform(put("/api/v1/team/members/{id}/role", MEMBER).with(manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"roleId\":\"" + roleId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MEMBER.toString()));
        mockMvc.perform(put("/api/v1/team/members/{id}/role", MEMBER).with(manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Choose the role to give this member."));
    }

    @Test
    void invite_acceptsARoleIdInPlaceOfTheOldRoleName() throws Exception {
        when(invitationService.invite(eq(TENANT), eq(USER), any(InviteUserRequest.class))).thenReturn(bob);

        mockMvc.perform(post("/api/v1/team/invitations").with(asUser(USER, TENANT, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Bob\",\"lastName\":\"Smith\",\"email\":\"bob@acme.io\","
                                + "\"roleId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated());
    }
}
