package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end API tests against a real Postgres: register → login → /auth/me,
 * plus the specified failure paths (wrong password → 401, duplicate
 * subdomain → 409) and the public job board (200 without auth).
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    // ------------------------------------------------------------- helpers

    /** Unique per test run so test data never collides across methods. */
    private static String uniqueSubdomain() {
        return "acme-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static Map<String, Object> registerBody(String subdomain, String email, String password) {
        return Map.of(
                "companyName", "Acme Inc",
                "subdomain", subdomain,
                "admin", Map.of(
                        "firstName", "Ada",
                        "lastName", "Lovelace",
                        "email", email,
                        "password", password));
    }

    private ResponseEntity<String> register(String subdomain, String email, String password) {
        return rest.postForEntity("/api/v1/auth/register",
                jsonEntity(registerBody(subdomain, email, password), null), String.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String subdomain, String email, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", email, "password", password), subdomain), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body,
                                                              String tenantSubdomain) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantSubdomain != null) {
            headers.set("X-Tenant-Subdomain", tenantSubdomain);
        }
        return new HttpEntity<>(body, headers);
    }

    // --------------------------------------------------------------- tests

    @Test
    void registerLoginMe_happyPath() {
        String subdomain = uniqueSubdomain();
        String email = "ada@acme.io";
        String password = "s3cret-password";

        // Register: 201, tenant + admin returned, and the raw response body
        // must not contain the password, a hash, or even the field name.
        ResponseEntity<String> registered = register(subdomain, email, password);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(registered.getBody()).contains("\"subdomain\":\"" + subdomain + "\"");
        assertThat(registered.getBody()).contains("\"role\":\"COMPANY_ADMIN\"");
        assertThat(registered.getBody()).doesNotContain(password);
        assertThat(registered.getBody()).doesNotContainIgnoringCase("password");
        assertThat(registered.getBody()).doesNotContain("$2a$", "$2b$");

        // Login: tokens + user, tenant resolved from the header.
        Map<String, Object> auth = login(subdomain, email, password);
        assertThat(auth).containsKeys("accessToken", "refreshToken", "expiresIn", "user");
        assertThat((Integer) auth.get("expiresIn")).isEqualTo(900); // 15 minutes

        // /auth/me with the access token: the caller's own profile.
        HttpHeaders bearer = new HttpHeaders();
        bearer.setBearerAuth((String) auth.get("accessToken"));
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(bearer), Map.class);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);
        assertThat(me.getBody().get("tenantName")).isEqualTo("Acme Inc");
    }

    @Test
    void login_wrongPassword_returns401Envelope() {
        String subdomain = uniqueSubdomain();
        register(subdomain, "ada@acme.io", "s3cret-password");

        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", "ada@acme.io", "password", "wrong-password"), subdomain),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        // Uniform envelope: { timestamp, status, error, message, path }
        assertThat(response.getBody()).containsKeys("timestamp", "status", "error", "message", "path");
        assertThat(response.getBody().get("status")).isEqualTo(401);
        assertThat(response.getBody().get("path")).isEqualTo("/api/v1/auth/login");
    }

    @Test
    void register_duplicateSubdomain_returns409Envelope() {
        String subdomain = uniqueSubdomain();
        assertThat(register(subdomain, "first@acme.io", "s3cret-password").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        ResponseEntity<String> duplicate = register(subdomain, "second@other.io", "s3cret-password");

        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).contains("\"status\":409");
    }

    @Test
    void refreshRotation_oldTokenDies_newTokenWorks() {
        String subdomain = uniqueSubdomain();
        register(subdomain, "ada@acme.io", "s3cret-password");
        Map<String, Object> auth = login(subdomain, "ada@acme.io", "s3cret-password");
        String originalRefreshToken = (String) auth.get("refreshToken");

        // First refresh succeeds and rotates.
        ResponseEntity<Map> refreshed = rest.postForEntity("/api/v1/auth/refresh",
                jsonEntity(Map.of("refreshToken", originalRefreshToken), null), Map.class);
        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().get("refreshToken")).isNotEqualTo(originalRefreshToken);

        // Replaying the consumed token must fail: rotation revoked it.
        ResponseEntity<Map> replay = rest.postForEntity("/api/v1/auth/refresh",
                jsonEntity(Map.of("refreshToken", originalRefreshToken), null), Map.class);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_withoutToken_returns401() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/auth/me", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("status")).isEqualTo(401);
    }

    @Test
    void publicJobs_requiresNoAuth_andReturnsEmptyPage() {
        ResponseEntity<Map> response = rest.getForEntity("/api/v1/public/jobs", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("content")).isEqualTo(java.util.List.of());
        assertThat(response.getBody().get("totalElements")).isEqualTo(0);
    }
}
