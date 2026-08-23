package com.talentpipe.tenant.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.tenant.dto.PublicCompanyProfileResponse;
import com.talentpipe.tenant.service.TenantService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code @WebMvcTest} slice tests for {@link PublicCompanyController}.
 *
 * <p>The public company endpoint requires no authentication and returns a
 * curated profile subset. These tests verify the HTTP contract, 404 behaviour
 * for unknown subdomains, and that the response contains the expected fields.</p>
 */
@WebMvcTest(PublicCompanyController.class)
@Import(TestSecurityConfig.class)
class PublicCompanyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TenantService tenantService;

    // ---------------------------------------------------------------- 200 — known subdomain

    @Test
    void getPublicProfile_knownSubdomain_returns200WithCuratedFields() throws Exception {
        PublicCompanyProfileResponse pubProfile = new PublicCompanyProfileResponse(
                "Acme Corp", "acme",
                "https://cdn.example.com/logo.png",
                null,
                "Great place to work",
                "We build awesome software",
                "Technology",
                "51-200",
                2015,
                "https://acme.io",
                "https://linkedin.com/company/acme",
                "https://x.com/acme",
                "https://instagram.com/acme",
                "https://facebook.com/acme",
                "Colombo",
                "Sri Lanka");

        when(tenantService.getPublicProfile("acme")).thenReturn(Optional.of(pubProfile));

        mockMvc.perform(get("/api/v1/public/companies/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Acme Corp"))
                .andExpect(jsonPath("$.subdomain").value("acme"))
                .andExpect(jsonPath("$.tagline").value("Great place to work"))
                .andExpect(jsonPath("$.description").value("We build awesome software"))
                .andExpect(jsonPath("$.industry").value("Technology"))
                .andExpect(jsonPath("$.city").value("Colombo"))
                .andExpect(jsonPath("$.country").value("Sri Lanka"))
                .andExpect(jsonPath("$.website").value("https://acme.io"))
                .andExpect(jsonPath("$.foundedYear").value(2015));
    }

    @Test
    void getPublicProfile_knownSubdomain_responseLacksSensitiveFields() throws Exception {
        PublicCompanyProfileResponse pubProfile = new PublicCompanyProfileResponse(
                "Acme Corp", "acme",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);

        when(tenantService.getPublicProfile("acme")).thenReturn(Optional.of(pubProfile));

        // Sensitive fields (email, hrEmail, planTier, status) are NOT part of
        // PublicCompanyProfileResponse; Jackson will not serialize them.
        mockMvc.perform(get("/api/v1/public/companies/acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.hrEmail").doesNotExist())
                .andExpect(jsonPath("$.planTier").doesNotExist())
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    // ---------------------------------------------------------------- 404 — unknown subdomain

    @Test
    void getPublicProfile_unknownSubdomain_returns404() throws Exception {
        when(tenantService.getPublicProfile("does-not-exist"))
                .thenReturn(Optional.empty());

        // The controller calls .orElseThrow(ResourceNotFoundException)
        mockMvc.perform(get("/api/v1/public/companies/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").exists());
    }

    // ---------------------------------------------------------------- no auth required

    @Test
    void getPublicProfile_withoutAuthHeader_isAllowed() throws Exception {
        // Public endpoint — no token needed
        PublicCompanyProfileResponse pub = new PublicCompanyProfileResponse(
                "Open Corp", "open-corp",
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null);

        when(tenantService.getPublicProfile("open-corp")).thenReturn(Optional.of(pub));

        // Perform request with NO Authorization header
        mockMvc.perform(get("/api/v1/public/companies/open-corp"))
                .andExpect(status().isOk());
    }
}
