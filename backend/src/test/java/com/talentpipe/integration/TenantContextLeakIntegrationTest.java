package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.common.tenant.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * THE tenant-isolation guard — treat this as the most important test in the
 * suite.
 *
 * <p>Servlet worker threads are pooled and reused across requests. If any
 * request left its tenant id behind in {@link TenantContext}, a later request
 * from a DIFFERENT tenant served by the same thread would observe it — a
 * cross-tenant data leak. The contract under test: TenantContext is EMPTY at
 * the very start and very end of EVERY request, while still being populated
 * in between for authenticated tenant users.</p>
 *
 * <p>Mechanics: a probe filter registered at {@code HIGHEST_PRECEDENCE} runs
 * OUTSIDE the entire security filter chain and records what it sees on the
 * worker thread immediately before and after the application handles each
 * request. A probe endpoint records the value mid-request to prove the test
 * isn't vacuously passing.</p>
 */
@Import({TenantContextLeakIntegrationTest.ProbeConfig.class, TestEmailConfig.class})
class TenantContextLeakIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private TestEmailConfig.CapturingEmailService emailCapture;

    @BeforeEach
    void resetProbe() {
        ContextProbeFilter.OBSERVED_AT_ENTRY.clear();
        ContextProbeFilter.OBSERVED_AT_EXIT.clear();
        emailCapture.clear();
    }

    @Test
    void tenantContext_isEmptyAtStartAndEndOfEveryRequest_yetSetDuringIt() {
        // Arrange: a real tenant + authenticated session.
        String subdomain = "leak-" + UUID.randomUUID().toString().substring(0, 8);
        rest.postForEntity("/api/v1/auth/register", json(Map.of(
                "companyName", "Leak Probe Inc",
                "subdomain", subdomain,
                "admin", Map.of(
                        "firstName", "Grace", "lastName", "Hopper",
                        "email", "grace@probe.io", "password", "s3cret-password"))), String.class);

        // Verify the admin's email first — unverified accounts get an
        // actionable 403 at login (PB-001), and we need a real session.
        String verificationLink = emailCapture.lastVerificationLink();
        String rawToken = verificationLink.substring(verificationLink.indexOf("token=") + 6);
        rest.postForEntity("/api/v1/auth/verify-email", json(Map.of("token", rawToken)), String.class);

        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_JSON);
        loginHeaders.set("X-Tenant-Subdomain", subdomain);
        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                new HttpEntity<>(Map.of("email", "grace@probe.io", "password", "s3cret-password"),
                        loginHeaders),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        String accessToken = (String) login.getBody().get("accessToken");
        String tenantId = (String) ((Map<?, ?>) login.getBody().get("user")).get("tenantId");

        // Act: several authenticated requests, which force worker threads to
        // carry a tenant id mid-request.
        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth(accessToken);
        for (int i = 0; i < 5; i++) {
            ResponseEntity<String> probe = rest.exchange("/api/v1/test/tenant-context",
                    HttpMethod.GET, new HttpEntity<>(bearer), String.class);
            assertThat(probe.getStatusCode()).isEqualTo(HttpStatus.OK);
            // Sanity: DURING the request the context really was populated with
            // the caller's tenant — otherwise this whole test proves nothing.
            assertThat(probe.getBody()).isEqualTo(tenantId);
        }

        // Assert: on the raw worker threads, the context was EMPTY at the
        // entry and exit of every single request (register, login, probes).
        assertThat(ContextProbeFilter.OBSERVED_AT_ENTRY)
                .isNotEmpty()
                .allSatisfy(seen -> assertThat(seen.isPresent()).as("stale tenant id at request START").isFalse());
        assertThat(ContextProbeFilter.OBSERVED_AT_EXIT)
                .hasSameSizeAs(ContextProbeFilter.OBSERVED_AT_ENTRY)
                .allSatisfy(seen -> assertThat(seen.isPresent()).as("tenant id LEAKED past request END").isFalse());
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    // ------------------------------------------------------- test fixtures

    /**
     * Records TenantContext state on the worker thread at the outermost edge
     * of the filter pipeline — before the security chain runs and after it
     * has fully unwound (i.e. after TenantResolvingFilter's finally block).
     */
    static class ContextProbeFilter implements Filter {

        record Observation(UUID tenantId) {
            boolean isPresent() {
                return tenantId != null;
            }
        }

        static final Queue<Observation> OBSERVED_AT_ENTRY = new ConcurrentLinkedQueue<>();
        static final Queue<Observation> OBSERVED_AT_EXIT = new ConcurrentLinkedQueue<>();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            OBSERVED_AT_ENTRY.add(new Observation(TenantContext.get()));
            try {
                chain.doFilter(request, response);
            } finally {
                OBSERVED_AT_EXIT.add(new Observation(TenantContext.get()));
            }
        }
    }

    /** Authenticated endpoint exposing the mid-request TenantContext value. */
    @RestController
    static class TenantContextProbeController {

        @GetMapping("/api/v1/test/tenant-context")
        public String currentTenant() {
            UUID tenantId = TenantContext.get();
            return tenantId == null ? "" : tenantId.toString();
        }
    }

    @TestConfiguration
    static class ProbeConfig {

        @Bean
        FilterRegistrationBean<ContextProbeFilter> contextProbeFilter() {
            var registration = new FilterRegistrationBean<>(new ContextProbeFilter());
            // Outermost filter: runs before (and unwinds after) everything
            // else, including the whole Spring Security chain.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return registration;
        }

        @Bean
        TenantContextProbeController tenantContextProbeController() {
            return new TenantContextProbeController();
        }
    }
}
