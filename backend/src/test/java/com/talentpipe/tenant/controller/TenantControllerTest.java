package com.talentpipe.tenant.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpipe.security.UserPrincipal;
import com.talentpipe.tenant.dto.CompanyProfileResponse;
import com.talentpipe.tenant.dto.UpdateCompanyProfileRequest;
import com.talentpipe.tenant.service.TenantMediaService;
import com.talentpipe.tenant.service.TenantService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * {@code @WebMvcTest} slice tests for {@link TenantController}.
 *
 * <p>Uses a custom {@link RequestPostProcessor} that injects a real
 * {@link UserPrincipal} into the {@code SecurityContext} — matching exactly
 * what {@link com.talentpipe.security.JwtAuthenticationFilter} does at runtime.
 * This gives accurate coverage of {@code @PreAuthorize} role checks and
 * {@code @AuthenticationPrincipal} resolution without a full application
 * context.</p>
 */
@WebMvcTest(TenantController.class)
@Import(TestSecurityConfig.class)
class TenantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TenantService tenantService;

    @MockitoBean
    private TenantMediaService tenantMediaService;

    private UUID tenantId;
    private CompanyProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        profileResponse = buildProfileResponse(tenantId);
    }

    // ---------------------------------------------------------------- GET /api/v1/tenant

    @Test
    void getProfile_asCompanyAdmin_returns200() throws Exception {
        when(tenantService.getProfile(any(UUID.class))).thenReturn(profileResponse);

        mockMvc.perform(get("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.subdomain").value("acme"));
    }

    @Test
    void getProfile_asHrManager_returns200() throws Exception {
        when(tenantService.getProfile(any(UUID.class))).thenReturn(profileResponse);

        mockMvc.perform(get("/api/v1/tenant")
                        .with(asPrincipal(tenantId, "HR_MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void getProfile_asInterviewer_returns200() throws Exception {
        when(tenantService.getProfile(any(UUID.class))).thenReturn(profileResponse);

        mockMvc.perform(get("/api/v1/tenant")
                        .with(asPrincipal(tenantId, "INTERVIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    void getProfile_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/tenant"))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- PATCH /api/v1/tenant — happy path

    @Test
    void updateProfile_validRequest_returns200WithProfile() throws Exception {
        when(tenantService.updateProfile(any(UUID.class), any(UpdateCompanyProfileRequest.class)))
                .thenReturn(profileResponse);

        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io");

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"));
    }

    // ---------------------------------------------------------------- PATCH — validation failures (400)

    @Test
    void updateProfile_blankName_returns400WithFieldError() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "",           // @NotBlank violation
                "email", "admin@acme.io");

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("name")));
    }

    @Test
    void updateProfile_invalidEmail_returns400WithFieldError() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "not-an-email");  // @Email violation

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("email")));
    }

    @Test
    void updateProfile_futureFoundedYear_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io",
                "foundedYear", 9999);   // @MaxCurrentYear violation

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("foundedYear")));
    }

    @Test
    void updateProfile_invalidWorkMode_returns400() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io",
                "workModes", List.of("Freelance"));  // @AllowedValues violation

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asCompanyAdmin(tenantId))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", Matchers.containsString("workModes")));
    }

    // ---------------------------------------------------------------- PATCH — authorization (403)

    @Test
    void updateProfile_asHrManager_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io");

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asPrincipal(tenantId, "HR_MANAGER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_asInterviewer_returns403() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io");

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(asPrincipal(tenantId, "INTERVIEWER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateProfile_withoutAuth_returns401() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Acme Corp",
                "email", "admin@acme.io");

        mockMvc.perform(patch("/api/v1/tenant")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Creates a {@link RequestPostProcessor} that populates the
     * {@code SecurityContext} with a {@link UserPrincipal} having
     * {@code COMPANY_ADMIN} role — matching what JwtAuthenticationFilter does.
     */
    private static RequestPostProcessor asCompanyAdmin(UUID tenantId) {
        return asPrincipal(tenantId, "COMPANY_ADMIN");
    }

    private static RequestPostProcessor asPrincipal(UUID tenantId, String role) {
        UserPrincipal principal = new UserPrincipal(
                UUID.randomUUID(), tenantId, role, "user@acme.io");
        return request -> {
            var auth = new UsernamePasswordAuthenticationToken(
                    principal, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role)));
            request.setAttribute(
                    SecurityMockMvcRequestPostProcessors.class.getName() + ".authentication", auth);
            return SecurityMockMvcRequestPostProcessors.authentication(auth).postProcessRequest(request);
        };
    }

    private static CompanyProfileResponse buildProfileResponse(UUID id) {
        return new CompanyProfileResponse(
                id, "Acme Corp", "acme", "STANDARD", "ACTIVE",
                null, null, null,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                Instant.now());
    }
}
