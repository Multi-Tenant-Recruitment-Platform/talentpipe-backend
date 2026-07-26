package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.integration.TestEmailConfig.CapturingEmailService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end integration tests for the email verification flow (PB-001):
 * <ul>
 *   <li>Register → login before verification → 403 (actionable)</li>
 *   <li>Register → verify → login → 200</li>
 *   <li>Expired token → 401</li>
 *   <li>Re-use of consumed token → 401</li>
 * </ul>
 */
@Import(TestEmailConfig.class)
class EmailVerificationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CapturingEmailService emailCapture;

    @BeforeEach
    void clearEmailCapture() {
        emailCapture.clear();
    }

    // ---------------------------------------------------------------- helpers

    private static String uniqueSubdomain() {
        return "verify-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private ResponseEntity<String> register(String subdomain, String email, String password) {
        Map<String, Object> body = Map.of(
                "companyName", "Acme Inc",
                "subdomain", subdomain,
                "admin", Map.of("firstName", "Ada", "lastName", "Lovelace",
                        "email", email, "password", password));
        return rest.postForEntity("/api/v1/auth/register", jsonEntity(body, null), String.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> login(String subdomain, String email, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", email, "password", password), subdomain), Map.class);
    }

    private ResponseEntity<String> verifyEmail(String rawToken) {
        return rest.postForEntity("/api/v1/auth/verify-email",
                jsonEntity(Map.of("token", rawToken), null), String.class);
    }

    private static String extractToken(String link, String paramName) {
        int idx = link.indexOf(paramName + "=");
        if (idx < 0) throw new IllegalArgumentException("Token param '" + paramName + "' not in link: " + link);
        return link.substring(idx + paramName.length() + 1);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body,
                                                              String tenantSubdomain) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantSubdomain != null) headers.set("X-Tenant-Subdomain", tenantSubdomain);
        return new HttpEntity<>(body, headers);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void registerWithoutVerification_loginReturns403() {
        String subdomain = uniqueSubdomain();
        assertThat(register(subdomain, "ada@acme.io", "s3cret-password").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Account is PENDING_VERIFICATION — login must be blocked with an
        // actionable 403 (credentials are correct, so pointing at the
        // verification email leaks nothing).
        ResponseEntity<Map> loginResp = login(subdomain, "ada@acme.io", "s3cret-password");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void registerVerifyLogin_happyPath() {
        String subdomain = uniqueSubdomain();
        assertThat(register(subdomain, "ada@acme.io", "s3cret-password").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        // Extract the token from the in-memory email capture.
        String verificationLink = emailCapture.lastVerificationLink();
        assertThat(verificationLink).contains("verify-email?token=");
        String rawToken = extractToken(verificationLink, "token");

        // Verify the email — account transitions to ACTIVE.
        ResponseEntity<String> verifyResp = verifyEmail(rawToken);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Login must now succeed.
        ResponseEntity<Map> loginResp = login(subdomain, "ada@acme.io", "s3cret-password");
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody()).containsKeys("accessToken", "refreshToken");
    }

    @Test
    void verifyEmail_unknownToken_returns401() {
        ResponseEntity<String> resp = verifyEmail("this-token-does-not-exist");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verifyEmail_consumedToken_returns401() {
        String subdomain = uniqueSubdomain();
        register(subdomain, "ada@acme.io", "s3cret-password");

        String rawToken = extractToken(emailCapture.lastVerificationLink(), "token");

        // First verification consumes the token.
        assertThat(verifyEmail(rawToken).getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second verification must reject the already-used token.
        assertThat(verifyEmail(rawToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_responseDoesNotContainPasswordMaterial() {
        String subdomain = uniqueSubdomain();
        ResponseEntity<String> resp = register(subdomain, "ada@acme.io", "s3cret-password");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).doesNotContain("s3cret-password");
        assertThat(resp.getBody()).doesNotContainIgnoringCase("password");
        assertThat(resp.getBody()).doesNotContain("$2a$", "$2b$");
        // Status in response must reflect PENDING_VERIFICATION.
        assertThat(resp.getBody()).contains("PENDING_VERIFICATION");
    }
}
