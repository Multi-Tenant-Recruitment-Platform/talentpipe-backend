package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for signing in the way the sign-in modal does it: email
 * and password only, no workspace. One email can belong to several accounts
 * (a person in two companies, or a company member who is also a job seeker),
 * so when the password fits more than one the API asks which one, and the
 * answer is a single-use, short-lived token exchanged at
 * {@code POST /auth/login/select}.
 */
@SuppressWarnings("unchecked")
class WorkspaceSelectionIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String SELECT = "/api/v1/auth/login/select";

    @Test
    void oneEmailInTwoCompanies_isAskedWhichOne_thenSignedInToTheChosenWorkspace() {
        String email = uniqueEmail();
        Workspace zeta = newWorkspace("Zeta Labs", subdomain(), email);
        Workspace acme = newWorkspace("Acme Inc", subdomain(), email);

        ResponseEntity<Map<String, Object>> asked = loginResponse(null, email, PASSWORD);

        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(asked.getBody()).containsEntry("code", "WORKSPACE_SELECTION_REQUIRED")
                .doesNotContainKey("accessToken");
        Map<String, Object> details = (Map<String, Object>) asked.getBody().get("details");
        assertThat(details).containsEntry("expiresInSeconds", 120).containsKey("selectionToken");
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) details.get("accounts");
        assertThat(accounts).extracting(account -> account.get("workspaceName"))
                .containsExactly("Acme Inc", "Zeta Labs");
        assertThat(accounts.get(1)).containsEntry("type", "COMPANY")
                .containsEntry("subdomain", zeta.subdomain())
                .containsEntry("roleName", "Owner");

        String selectionToken = (String) details.get("selectionToken");
        Object zetaAccount = accounts.get(1).get("accountId");

        // An account the password was never checked against cannot be picked.
        ResponseEntity<Map<String, Object>> notOffered = select(selectionToken, UUID.randomUUID());
        assertThat(notOffered.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map<String, Object>> chosen = select(selectionToken, zetaAccount);
        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> user = (Map<String, Object>) chosen.getBody().get("user");
        assertThat(user).containsEntry("tenantId", zeta.tenantId().toString())
                .containsEntry("tenantName", "Zeta Labs");

        Map<String, Object> me = call(HttpMethod.GET, "/api/v1/auth/me",
                (String) chosen.getBody().get("accessToken"), null).getBody();
        assertThat(me).containsEntry("tenantName", "Zeta Labs");

        // Single use.
        ResponseEntity<Map<String, Object>> replayed = select(selectionToken, zetaAccount);
        assertThat(replayed.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(replayed.getBody()).containsEntry("code", "INVALID_TOKEN");

        // Naming the workspace still signs straight in, no question asked.
        assertThat(loginResponse(acme.subdomain(), email, PASSWORD).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aCompanyMemberWhoIsAlsoACandidate_canChooseTheCandidateAccount() {
        String email = uniqueEmail();
        newWorkspace("Acme Inc", subdomain(), email);
        registerVerifiedCandidate(email);

        ResponseEntity<Map<String, Object>> asked = loginResponse(null, email, PASSWORD);

        assertThat(asked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        Map<String, Object> details = (Map<String, Object>) asked.getBody().get("details");
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) details.get("accounts");
        assertThat(accounts).extracting(account -> account.get("type")).containsExactly("COMPANY", "CANDIDATE");

        ResponseEntity<Map<String, Object>> chosen =
                select((String) details.get("selectionToken"), accounts.get(1).get("accountId"));

        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) chosen.getBody().get("user"))
                .containsEntry("role", "CANDIDATE")
                .containsEntry("email", email);
    }

    @Test
    void choosingAnAccountThatIsNotReady_explainsWhy_andAnotherCanStillBeChosen() {
        String email = uniqueEmail();
        Workspace acme = newWorkspace("Acme Inc", subdomain(), email);
        // A second company registered with the same email but never verified.
        rest.postForEntity("/api/v1/auth/register", json(Map.of(
                "companyName", "Beta Co",
                "subdomain", subdomain(),
                "admin", Map.of("firstName", "Ada", "lastName", "Lovelace",
                        "email", email, "password", PASSWORD)), null), String.class);

        Map<String, Object> details = (Map<String, Object>) loginResponse(null, email, PASSWORD)
                .getBody().get("details");
        String selectionToken = (String) details.get("selectionToken");
        List<Map<String, Object>> accounts = (List<Map<String, Object>>) details.get("accounts");
        assertThat(accounts).extracting(account -> account.get("workspaceName"))
                .containsExactly("Acme Inc", "Beta Co");

        ResponseEntity<Map<String, Object>> unverified = select(selectionToken, accounts.get(1).get("accountId"));
        assertThat(unverified.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unverified.getBody()).containsEntry("code", "ACCOUNT_NOT_VERIFIED");

        // The refusal did not use the token up.
        ResponseEntity<Map<String, Object>> chosen = select(selectionToken, accounts.get(0).get("accountId"));
        assertThat(chosen.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Map<String, Object>) chosen.getBody().get("user"))
                .containsEntry("tenantId", acme.tenantId().toString());
    }

    @Test
    void aWrongPassword_withSeveralAccounts_isThePlain401() {
        String email = uniqueEmail();
        newWorkspace("Acme Inc", subdomain(), email);
        newWorkspace("Zeta Labs", subdomain(), email);

        ResponseEntity<Map<String, Object>> response = loginResponse(null, email, "wrong-password");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "INVALID_CREDENTIALS").doesNotContainKey("details");
    }

    @Test
    void anUnknownSelectionToken_isRefused() {
        ResponseEntity<Map<String, Object>> response = select("not-a-real-token", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).containsEntry("code", "INVALID_TOKEN");
    }

    @Test
    void aChoiceMissingItsFields_isRejectedFieldByField() {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, SELECT, null, Map.of());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).containsEntry("code", "VALIDATION_FAILED");
        assertThat((List<Map<String, Object>>) response.getBody().get("fieldErrors"))
                .extracting(error -> error.get("field"))
                .containsExactly("accountId", "selectionToken");
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<Map<String, Object>> select(String selectionToken, Object accountId) {
        return call(HttpMethod.POST, SELECT, null,
                Map.of("selectionToken", selectionToken, "accountId", accountId.toString()));
    }

    private void registerVerifiedCandidate(String email) {
        ResponseEntity<String> registered = rest.postForEntity("/api/v1/public/candidates/register", json(Map.of(
                "fullName", "Ada Lovelace",
                "identityCardNumber", "941234567V",
                "address", "123 Galle Road, Colombo",
                "contactNumber", "+94 77 123 4567",
                "email", email,
                "password", PASSWORD), null), String.class);
        assertThat(registered.getStatusCode()).as("candidate sign-up: %s", registered.getBody())
                .isEqualTo(HttpStatus.CREATED);
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();
    }

    private static String subdomain() {
        return "ws-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueEmail() {
        return "ada-" + UUID.randomUUID().toString().substring(0, 8) + "@example.io";
    }
}
