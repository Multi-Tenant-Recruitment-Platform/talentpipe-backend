package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * What production behind CloudFront and a load balancer relies on, over real
 * HTTP, because both live in the servlet container, where MockMvc never goes.
 *
 * <ul>
 *   <li>The health probes answer without a token, and say nothing more than
 *       UP or DOWN. No other actuator endpoint is reachable.</li>
 *   <li>With the production settings (docs/DEPLOYMENT.md), the login rate
 *       limit counts by the address CloudFront puts in its header, not by the
 *       load balancer's address, which every request would share, and not by
 *       anything the caller can write.</li>
 * </ul>
 */
class DeploymentReadinessIntegrationTest extends AbstractIntegrationTest {

    /** The header the CloudFront function sets to the viewer's address. */
    private static final String VIEWER_IP = "X-Viewer-Ip";

    @DynamicPropertySource
    static void productionEdgeSettings(DynamicPropertyRegistry registry) {
        registry.add("server.forward-headers-strategy", () -> "native");
        registry.add("server.tomcat.remoteip.remote-ip-header", () -> VIEWER_IP);
        registry.add("talentpipe.security.login-rate-limit.max-per-minute", () -> "2");
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    @SuppressWarnings("rawtypes")
    void livenessAnswersWithoutAToken() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health/liveness", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void readinessChecksTheDatabaseAndAnswersWithoutAToken() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health/readiness", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void healthGivesNoDetails() {
        ResponseEntity<Map> response = rest.getForEntity("/actuator/health", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsOnlyKeys("status", "groups");
    }

    @Test
    void noOtherActuatorEndpointIsReachable() {
        for (String path : new String[] {"/actuator/env", "/actuator/info", "/actuator/beans",
                "/actuator/configprops", "/actuator/heapdump", "/actuator/loggers"}) {
            ResponseEntity<String> response = rest.getForEntity(path, String.class);
            assertThat(response.getStatusCode()).as(path).isIn(HttpStatus.UNAUTHORIZED, HttpStatus.NOT_FOUND);
        }
    }

    @Test
    void theLoginLimitCountsByTheViewerAddressCloudFrontReports() {
        assertThat(login("203.0.113.10", null)).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("203.0.113.10", null)).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("203.0.113.10", null)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        // Another viewer behind the same load balancer is not affected.
        assertThat(login("203.0.113.11", null)).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void aForgedForwardedForDoesNotBuyAFreshAllowance() {
        assertThat(login("198.51.100.20", "10.9.8.1")).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("198.51.100.20", "10.9.8.2")).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(login("198.51.100.20", "10.9.8.3")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    private HttpStatus login(String viewerIp, String forgedForwardedFor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(VIEWER_IP, viewerIp);
        if (forgedForwardedFor != null) {
            headers.set("X-Forwarded-For", forgedForwardedFor);
        }
        String body = "{\"email\":\"nobody@example.com\",\"password\":\"not-the-password\"}";
        ResponseEntity<String> response = rest.exchange("/api/v1/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        return HttpStatus.valueOf(response.getStatusCode().value());
    }
}
