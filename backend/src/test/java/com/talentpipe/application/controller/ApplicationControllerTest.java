package com.talentpipe.application.controller;

import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asMemberWith;
import static com.talentpipe.testsupport.WebMvcSecurityTestConfig.asUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.application.dto.ApplicationResponse;
import com.talentpipe.application.service.ApplicantViewer;
import com.talentpipe.application.service.ApplicationService;
import com.talentpipe.security.Authorities;
import com.talentpipe.security.Permission;
import com.talentpipe.security.UserPrincipal;
import com.talentpipe.testsupport.WebMvcSecurityTestConfig;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Who may list and move a company's applicants. Listing is open to every
 * member but narrowed by the service; moving needs {@code applications.manage}.
 * Neither listens to the old fixed role names any more.
 */
@WebMvcTest(ApplicationController.class)
@Import(WebMvcSecurityTestConfig.class)
class ApplicationControllerTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final String STATUS_BODY = "{\"status\":\"REJECTED\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    // ------------------------------------------------------------ listing

    @Test
    void listing_asARecruiter_seesEveryApplicant() throws Exception {
        when(applicationService.forTenant(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/applications/tenant").with(asUser(USER_ID, TENANT_ID, "HR_MANAGER")))
                .andExpect(status().isOk());

        verify(applicationService).forTenant(new ApplicantViewer(TENANT_ID, USER_ID, true));
    }

    @Test
    void listing_asAnInterviewer_isNarrowedToTheirVacancies() throws Exception {
        when(applicationService.forTenant(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/applications/tenant").with(asUser(USER_ID, TENANT_ID, "INTERVIEWER")))
                .andExpect(status().isOk());

        verify(applicationService).forTenant(new ApplicantViewer(TENANT_ID, USER_ID, false));
    }

    @Test
    void listing_asACustomRoleWithViewAll_seesEveryApplicant() throws Exception {
        when(applicationService.forTenant(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/applications/tenant")
                        .with(asMemberWith(USER_ID, TENANT_ID, Permission.APPLICATIONS_VIEW_ALL)))
                .andExpect(status().isOk());

        verify(applicationService).forTenant(new ApplicantViewer(TENANT_ID, USER_ID, true));
    }

    @Test
    void listing_asACandidate_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/applications/tenant").with(asUser(USER_ID, null, "CANDIDATE")))
                .andExpect(status().isForbidden());
        verify(applicationService, never()).forTenant(any());
    }

    @Test
    void listing_withoutAToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/applications/tenant")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------- moving

    @Test
    void moving_asARecruiter_isAllowed() throws Exception {
        when(applicationService.updateStatus(any(), eq(APPLICATION_ID), any())).thenReturn(response());

        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID))
                        .with(asUser(USER_ID, TENANT_ID, "HR_MANAGER")))
                .andExpect(status().isOk());

        verify(applicationService).updateStatus(eq(new ApplicantViewer(TENANT_ID, USER_ID, true)),
                eq(APPLICATION_ID), any());
    }

    @Test
    void moving_asACustomRoleThatMovesApplicants_isAllowed_withinTheirVacancies() throws Exception {
        when(applicationService.updateStatus(any(), eq(APPLICATION_ID), any())).thenReturn(response());

        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID))
                        .with(asMemberWith(USER_ID, TENANT_ID, Permission.APPLICATIONS_MANAGE)))
                .andExpect(status().isOk());

        verify(applicationService).updateStatus(eq(new ApplicantViewer(TENANT_ID, USER_ID, false)),
                eq(APPLICATION_ID), any());
    }

    /** The bug this replaces: every interviewer could reject any applicant in the company. */
    @Test
    void moving_asAnInterviewer_isForbidden() throws Exception {
        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID))
                        .with(asUser(USER_ID, TENANT_ID, "INTERVIEWER")))
                .andExpect(status().isForbidden());
        verify(applicationService, never()).updateStatus(any(), any(), any());
    }

    @Test
    void moving_asACustomRoleThatOnlySeesApplicants_isForbidden() throws Exception {
        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID))
                        .with(asMemberWith(USER_ID, TENANT_ID, Permission.APPLICATIONS_VIEW_ALL)))
                .andExpect(status().isForbidden());
    }

    /**
     * A Recruiter whose role an Owner edited to drop "Move applicants" keeps
     * the transitional ROLE_HR_MANAGER authority. It must not open anything.
     */
    @Test
    void theOldRoleNameAlone_opensNothing() throws Exception {
        RequestPostProcessor editedRecruiter = legacyRoleOnly("HR_MANAGER");

        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID)).with(editedRecruiter))
                .andExpect(status().isForbidden());
        when(applicationService.forTenant(any())).thenReturn(List.of());
        mockMvc.perform(get("/api/v1/applications/tenant").with(editedRecruiter))
                .andExpect(status().isOk());
        verify(applicationService).forTenant(new ApplicantViewer(TENANT_ID, USER_ID, false));
    }

    @Test
    void moving_asACandidate_isForbidden() throws Exception {
        mockMvc.perform(withBody(patch("/api/v1/applications/{id}/status", APPLICATION_ID))
                        .with(asUser(USER_ID, null, "CANDIDATE")))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ helpers

    private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder request) {
        return request.contentType(MediaType.APPLICATION_JSON).content(STATUS_BODY);
    }

    /** A company member holding no permission at all, only the transitional fixed-role authority. */
    private static RequestPostProcessor legacyRoleOnly(String role) {
        UserPrincipal principal = new UserPrincipal(USER_ID, TENANT_ID, role, "member@example.com");
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(
                new SimpleGrantedAuthority(Authorities.COMPANY_MEMBER),
                new SimpleGrantedAuthority(Authorities.ROLE_PREFIX + role))));
    }

    private static ApplicationResponse response() {
        return new ApplicationResponse(APPLICATION_ID, UUID.randomUUID(), UUID.randomUUID(), TENANT_ID,
                "Backend Engineer", "REJECTED", Instant.now(), Instant.now(), null);
    }
}
