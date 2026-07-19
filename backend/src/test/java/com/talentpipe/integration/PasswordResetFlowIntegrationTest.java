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
 * End-to-end integration tests for the password reset flow (PB-008):
 * <ul>
 *   <li>Full cycle: reset request → confirm → login with new password</li>
 *   <li>Old password rejected after reset</li>
 *   <li>Old refresh token rejected after reset (session revocation)</li>
 *   <li>Expired / used token rejection</li>
 *   <li>Unknown email → always 200 (no enumeration)</li>
 * </ul>
 */
@Import(TestEmailConfig.class)
class PasswordResetFlowIntegrationTest extends AbstractIntegrationTest {

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
        return "reset-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Register + verify so the account is ACTIVE and ready for login. */
    private String registerAndActivate(String subdomain, String email, String password) {
        Map<String, Object> body = Map.of(
                "companyName", "Acme Inc",
                "subdomain", subdomain,
                "admin", Map.of("firstName", "Ada", "lastName", "Lovelace",
                        "email", email, "password", password));
        rest.postForEntity("/api/v1/auth/register", jsonEntity(body, null), String.class);

        String rawVerifyToken = extractToken(emailCapture.lastVerificationLink(), "token");
        rest.postForEntity("/api/v1/auth/verify-email",
                jsonEntity(Map.of("token", rawVerifyToken), null), String.class);
        emailCapture.clear();
        return subdomain;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> login(String subdomain, String email, String password) {
        ResponseEntity<Map> resp = rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", email, "password", password), subdomain), Map.class);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        return resp.getBody();
    }

    private ResponseEntity<Map> tryLogin(String subdomain, String email, String password) {
        return rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", email, "password", password), subdomain), Map.class);
    }

    private ResponseEntity<String> requestReset(String email, String subdomain) {
        return rest.postForEntity("/api/v1/auth/password-reset/request",
                jsonEntity(Map.of("email", email, "subdomain", subdomain), null), String.class);
    }

    private ResponseEntity<String> confirmReset(String rawToken, String newPassword) {
        return rest.postForEntity("/api/v1/auth/password-reset/confirm",
                jsonEntity(Map.of("token", rawToken, "newPassword", newPassword), null),
                String.class);
    }

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> refresh(String refreshToken) {
        return rest.postForEntity("/api/v1/auth/refresh",
                jsonEntity(Map.of("refreshToken", refreshToken), null), Map.class);
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
    void passwordReset_fullCycle_newPasswordWorks() {
        String subdomain = uniqueSubdomain();
        registerAndActivate(subdomain, "ada@acme.io", "original-password");

        // Request reset.
        ResponseEntity<String> resetReq = requestReset("ada@acme.io", subdomain);
        assertThat(resetReq.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Capture token from in-memory email.
        String rawToken = extractToken(emailCapture.lastResetLink(), "token");

        // Confirm reset with new password.
        ResponseEntity<String> confirm = confirmReset(rawToken, "brand-new-password");
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Login with new password must succeed.
        assertThat(tryLogin(subdomain, "ada@acme.io", "brand-new-password").getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void passwordReset_oldPasswordRejected_after_reset() {
        String subdomain = uniqueSubdomain();
        registerAndActivate(subdomain, "ada@acme.io", "original-password");

        requestReset("ada@acme.io", subdomain);
        String rawToken = extractToken(emailCapture.lastResetLink(), "token");
        confirmReset(rawToken, "brand-new-password");

        // Old password must no longer work.
        assertThat(tryLogin(subdomain, "ada@acme.io", "original-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void passwordReset_oldRefreshTokenRevoked_after_reset() {
        String subdomain = uniqueSubdomain();
        registerAndActivate(subdomain, "ada@acme.io", "original-password");

        // Capture refresh token from login before the reset.
        Map<String, Object> auth = login(subdomain, "ada@acme.io", "original-password");
        String oldRefreshToken = (String) auth.get("refreshToken");

        // Reset password.
        requestReset("ada@acme.io", subdomain);
        String rawToken = extractToken(emailCapture.lastResetLink(), "token");
        confirmReset(rawToken, "brand-new-password");

        // The old refresh token must be rejected.
        ResponseEntity<Map> replay = refresh(oldRefreshToken);
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void confirmReset_unknownToken_returns401() {
        ResponseEntity<String> resp = confirmReset("this-token-does-not-exist", "new-pass-word");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void confirmReset_consumedToken_returns401() {
        String subdomain = uniqueSubdomain();
        registerAndActivate(subdomain, "ada@acme.io", "original-password");

        requestReset("ada@acme.io", subdomain);
        String rawToken = extractToken(emailCapture.lastResetLink(), "token");

        // First confirm consumes the token.
        assertThat(confirmReset(rawToken, "first-new-password").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // Second confirm must be rejected.
        assertThat(confirmReset(rawToken, "second-new-password").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void requestReset_unknownEmail_always200_noEnumeration() {
        // Must return 200 even for non-existent email — no user enumeration.
        ResponseEntity<String> resp = requestReset("ghost@nowhere.io", "non-existent-subdomain");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void requestReset_resetsLinkExpiry_onSecondRequest() {
        String subdomain = uniqueSubdomain();
        registerAndActivate(subdomain, "ada@acme.io", "original-password");

        // First request.
        requestReset("ada@acme.io", subdomain);
        String firstToken = extractToken(emailCapture.lastResetLink(), "token");
        emailCapture.clear();

        // Second request — issues a new token, superseding the first.
        requestReset("ada@acme.io", subdomain);
        String secondToken = extractToken(emailCapture.lastResetLink(), "token");

        // The second token must work.
        assertThat(confirmReset(secondToken, "new-password-from-second").getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // The first token must have been invalidated (deleted before second was issued).
        assertThat(confirmReset(firstToken, "new-password-from-first").getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
