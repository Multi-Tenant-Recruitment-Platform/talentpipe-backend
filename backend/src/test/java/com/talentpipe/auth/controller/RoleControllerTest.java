package com.talentpipe.auth.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asMemberWith;
import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.auth.dto.RoleRequest;
import com.talentpipe.auth.dto.RoleResponse;
import com.talentpipe.auth.service.TenantRoleService;
import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.security.Permission;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice for {@link RoleController}: which permission each
 * endpoint requires, validation, and that tenant and actor come from the token.
 */
@WebMvcTest(RoleController.class)
@Import(WebMvcSecurityTestConfig.class)
class RoleControllerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID ROLE = UUID.randomUUID();
    private static final String BODY =
            "{\"name\":\"Hiring Coordinator\",\"permissions\":[\"team.invite\"],\"version\":0}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantRoleService roleService;

    private final RoleResponse role = new RoleResponse(ROLE, "Hiring Coordinator", null, null, false, true,
            List.of("team.view", "team.invite"), 0, 0, Instant.now(), Instant.now());

    @Test
    void catalogue_isOpenToAnyCompanyMember_butNotToCandidates() throws Exception {
        when(roleService.permissionCatalogue()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/permissions").with(asMemberWith(USER, TENANT)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/permissions").with(asUser(USER, null, "CANDIDATE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/permissions")).andExpect(status().isUnauthorized());
    }

    @Test
    void readingRoles_needsTeamView() throws Exception {
        when(roleService.listRoles(TENANT)).thenReturn(List.of(role));

        mockMvc.perform(get("/api/v1/roles").with(asMemberWith(USER, TENANT, Permission.TEAM_VIEW)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Hiring Coordinator"))
                .andExpect(jsonPath("$[0].permissions[1]").value("team.invite"));
        mockMvc.perform(get("/api/v1/roles").with(asMemberWith(USER, TENANT, Permission.JOBS_MANAGE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void changingRoles_needsRolesManage_onEveryWrite() throws Exception {
        var withoutIt = asMemberWith(USER, TENANT, Permission.TEAM_VIEW, Permission.TEAM_MANAGE);

        mockMvc.perform(post("/api/v1/roles").with(withoutIt).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/roles/{id}", ROLE).with(withoutIt)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/roles/{id}/duplicate", ROLE).with(withoutIt)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/roles/{id}", ROLE).with(withoutIt)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/roles/audit").with(withoutIt)).andExpect(status().isForbidden());

        verifyNoInteractions(roleService);
    }

    @Test
    void create_withRolesManage_answers201_andTakesTenantAndActorFromTheToken() throws Exception {
        when(roleService.createRole(eq(TENANT), eq(USER), any(RoleRequest.class))).thenReturn(role);

        mockMvc.perform(post("/api/v1/roles").with(asMemberWith(USER, TENANT, Permission.TEAM_VIEW, Permission.ROLES_MANAGE))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(ROLE.toString()));
    }

    @Test
    void create_withoutAName_isAValidationErrorWithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/roles").with(asUser(USER, TENANT, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"permissions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Give the role a name."));
    }

    @Test
    void delete_passesTheReassignmentThrough_andAnswers204() throws Exception {
        UUID target = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/roles/{id}", ROLE).param("reassignTo", target.toString())
                        .with(asUser(USER, TENANT, "COMPANY_ADMIN")))
                .andExpect(status().isNoContent());

        verify(roleService).deleteRole(TENANT, USER, ROLE, target);
    }

    @Test
    void aRefusedChange_answers422WithTheBusinessRuleCode() throws Exception {
        when(roleService.updateRole(eq(TENANT), eq(USER), eq(ROLE), any(RoleRequest.class)))
                .thenThrow(new BusinessRuleException("The Owner role can't be changed. It always has every permission."));

        mockMvc.perform(put("/api/v1/roles/{id}", ROLE).with(asUser(USER, TENANT, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value("The Owner role can't be changed. It always has every permission."));
    }
}
