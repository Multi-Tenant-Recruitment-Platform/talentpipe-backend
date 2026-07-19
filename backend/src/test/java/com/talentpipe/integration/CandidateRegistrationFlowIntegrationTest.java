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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end integration tests for Candidate registration and login flows (PB-006 & PB-007):
 * <ul>
 *   <li>Candidate registers -> returns 201 Created and PENDING_VERIFICATION</li>
 *   <li>Attempt login before verification -> returns 401</li>
 *   <li>Verify candidate email -> returns 200</li>
 *   <li>Candidate logs in -> returns valid JWT and Candidate profile details</li>
 *   <li>Candidate checks profile via /auth/me -> returns Candidate details</li>
 * </ul>
 */
@Import(TestEmailConfig.class)
class CandidateRegistrationFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CapturingEmailService emailCapture;

    @BeforeEach
    void clearEmailCapture() {
        emailCapture.clear();
    }

    // ------------------------------------------------------------- helpers

    private ResponseEntity<Map> registerCandidate(String email, String password, String name) {
        Map<String, Object> body = Map.of(
                "fullName", name,
                "identityCardNumber", "941234567V",
                "address", "123 Galle Road, Colombo",
                "contactNumber", "+94 77 123 4567",
                "email", email,
                "password", password
        );
        return rest.postForEntity("/api/v1/public/candidates/register", jsonEntity(body), Map.class);
    }

    private ResponseEntity<Map> loginCandidate(String email, String password) {
        // Candidate login passes no subdomain / X-Tenant-Subdomain header
        Map<String, String> body = Map.of("email", email, "password", password);
        return rest.postForEntity("/api/v1/auth/login", jsonEntity(body), Map.class);
    }

    private ResponseEntity<Void> verifyEmail(String rawToken) {
        return rest.postForEntity("/api/v1/auth/verify-email",
                jsonEntity(Map.of("token", rawToken)), Void.class);
    }

    private static String extractToken(String link) {
        int idx = link.indexOf("token=");
        if (idx < 0) throw new IllegalArgumentException("Token param not found in link: " + link);
        return link.substring(idx + 6);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>((Map<String, Object>) body, headers);
    }

    // --------------------------------------------------------------- tests

    @Test
    void registerVerifyLogin_candidateFlow_happyPath() {
        String email = "candidate-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String password = "secure-candidate-password";
        String name = "Sanduni Herath";

        // 1. Candidate Registration
        ResponseEntity<Map> regResp = registerCandidate(email, password, name);
        assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(regResp.getBody().get("email")).isEqualTo(email);
        assertThat(regResp.getBody().get("role")).isEqualTo("CANDIDATE");
        assertThat(regResp.getBody().get("status")).isEqualTo("PENDING_VERIFICATION");

        // 2. Try Login before Verification -> Should Fail (401)
        ResponseEntity<Map> initialLogin = loginCandidate(email, password);
        assertThat(initialLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // 3. Extract token & verify email
        String link = emailCapture.lastVerificationLink();
        String token = extractToken(link);
        ResponseEntity<Void> verifyResp = verifyEmail(token);
        assertThat(verifyResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. Try Login after Verification -> Should Succeed
        ResponseEntity<Map> loginResp = loginCandidate(email, password);
        assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResp.getBody()).containsKeys("accessToken", "refreshToken", "user");

        Map<String, Object> userMap = (Map<String, Object>) loginResp.getBody().get("user");
        assertThat(userMap.get("email")).isEqualTo(email);
        assertThat(userMap.get("role")).isEqualTo("CANDIDATE");
        assertThat(userMap.get("status")).isEqualTo("ACTIVE");

        // 5. Access /auth/me -> Should return Candidate details
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth((String) loginResp.getBody().get("accessToken"));
        ResponseEntity<Map> meResp = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);

        assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResp.getBody().get("email")).isEqualTo(email);
        assertThat(meResp.getBody().get("role")).isEqualTo("CANDIDATE");
    }

    @Test
    void registerCandidate_duplicateEmail_returns409Conflict() {
        String email = "duplicate-candidate@example.com";
        registerCandidate(email, "password123", "Sanduni Herath");

        // Attempt duplicate registration
        ResponseEntity<Map> conflictResp = registerCandidate(email, "password123", "Sanduni Herath");
        assertThat(conflictResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
