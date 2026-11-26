package com.talentpipe.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityHealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.availability.AvailabilityProbesAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.availability.ApplicationAvailabilityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The production {@link SecurityConfig}, loaded in a web slice with the
 * actuator's health endpoint and no database.
 *
 * <p>Controller slice tests use a stand-in security configuration
 * ({@code WebMvcSecurityTestConfig}), because they test controllers. This
 * test is about the real rules, the ones the full-stack integration tests
 * cover too, and it runs wherever unit tests run.</p>
 */
@WebMvcTest(
        controllers = SecurityConfigWebTest.ProbeController.class,
        properties = {
                // The readiness group includes the database, which a web slice has none of.
                "management.endpoint.health.validate-group-membership=false",
                "talentpipe.security.login-rate-limit.max-per-minute=3"
        })
@Import({SecurityConfig.class, SecurityErrorWriter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityConfigWebTest.ProbeController.class})
@ImportAutoConfiguration({
        EndpointAutoConfiguration.class,
        WebEndpointAutoConfiguration.class,
        HealthContributorAutoConfiguration.class,
        HealthEndpointAutoConfiguration.class,
        ApplicationAvailabilityAutoConfiguration.class,
        AvailabilityHealthContributorAutoConfiguration.class,
        AvailabilityProbesAutoConfiguration.class,
        ManagementContextAutoConfiguration.class,
        ServletManagementContextAutoConfiguration.class
})
class SecurityConfigWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private PermissionResolver permissionResolver;

    @Test
    void theHealthProbesAnswerWithoutAToken() throws Exception {
        for (String probe : new String[] {"/actuator/health/liveness", "/actuator/health/readiness"}) {
            mvc.perform(get(probe))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.components").doesNotExist());
        }
    }

    @Test
    void otherActuatorPathsNeedAToken() throws Exception {
        for (String path : new String[] {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/heapdump"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void theApiNeedsAToken() throws Exception {
        mvc.perform(get("/api/v1/probe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void publicPathsAreOpen() throws Exception {
        mvc.perform(get("/api/v1/public/probe")).andExpect(status().isOk());
    }

    @Test
    void loginAttemptsAreRateLimited() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().is4xxClientError())
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(429));
        }
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests());
    }

    @RestController
    static class ProbeController {

        @GetMapping("/api/v1/probe")
        String probe() {
            return "ok";
        }

        @GetMapping("/api/v1/public/probe")
        String publicProbe() {
            return "ok";
        }
    }
}
