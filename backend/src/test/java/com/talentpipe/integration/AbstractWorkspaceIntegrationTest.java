package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.integration.TestEmailConfig.CapturingEmailService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared plumbing for integration tests that act as company users: creating a
 * workspace, bringing people into it with a role, and calling the API as them.
 *
 * <p>Everything goes through the real HTTP API — registration, email
 * verification, login, invitations — so a test's data belongs to a tenant
 * created the same way a customer's would be. The database is emptied before
 * each test by {@link AbstractIntegrationTest}; tables owned by a tenant are
 * covered by that because they cascade from {@code tenants}.</p>
 */
@Import(TestEmailConfig.class)
abstract class AbstractWorkspaceIntegrationTest extends AbstractIntegrationTest {

    protected static final String PASSWORD = "s3cret-password";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected CapturingEmailService emailCapture;

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void clearEmailCapture() {
        emailCapture.clear();
    }

    /** A registered company and its first admin (its Owner), signed in. */
    protected record Workspace(String subdomain, String companyName, UUID tenantId, String adminToken) {
    }

    /** A team member who has accepted their invitation and signed in. */
    protected record Member(UUID id, String email, String token) {
    }

    // ------------------------------------------------------------ workspaces

    /** Registers a company, verifies its admin's email, and signs the admin in. */
    protected Workspace newWorkspace(String companyName) {
        String subdomain = "ws-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + subdomain + ".io";
        return newWorkspace(companyName, subdomain, email);
    }

    /** Registers a company with a given admin email — for tests about one email in two companies. */
    protected Workspace newWorkspace(String companyName, String subdomain, String adminEmail) {
        rest.postForEntity("/api/v1/auth/register", json(Map.of(
                "companyName", companyName,
                "subdomain", subdomain,
                "admin", Map.of("firstName", "Ada", "lastName", "Lovelace",
                        "email", adminEmail, "password", PASSWORD)), null), String.class);
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();

        String token = login(subdomain, adminEmail);
        Map<String, Object> profile = call(HttpMethod.GET, "/api/v1/tenant", token, null).getBody();
        return new Workspace(subdomain, companyName, UUID.fromString((String) profile.get("id")), token);
    }

    /**
     * Invites a team member with one of the old role names, accepts the
     * invitation and signs them in.
     *
     * @param role {@code HR_MANAGER} or {@code INTERVIEWER}
     * @return the new member's access token
     */
    protected String newMember(Workspace workspace, String role) {
        return join(workspace, Map.of("role", role), role.toLowerCase()).token();
    }

    /** Invites a team member into a given role, accepts the invitation and signs them in. */
    protected Member newMemberWithRole(Workspace workspace, UUID roleId) {
        return join(workspace, Map.of("roleId", roleId.toString()), "member");
    }

    /** The id of the first user in the workspace with the given (old) role name. */
    @SuppressWarnings("unchecked")
    protected UUID userIdWithRole(Workspace workspace, String role) {
        List<Map<String, Object>> team = rest.exchange("/api/v1/team", HttpMethod.GET,
                new HttpEntity<>(bearer(workspace.adminToken())), List.class).getBody();
        return team.stream()
                .filter(member -> role.equals(member.get("role")))
                .map(member -> UUID.fromString((String) member.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No " + role + " in workspace"));
    }

    /** The id of the workspace's role with this name, from {@code GET /roles}. */
    @SuppressWarnings("unchecked")
    protected UUID roleId(Workspace workspace, String roleName) {
        List<Map<String, Object>> roles = rest.exchange("/api/v1/roles", HttpMethod.GET,
                new HttpEntity<>(bearer(workspace.adminToken())), List.class).getBody();
        return roles.stream()
                .filter(role -> roleName.equals(role.get("name")))
                .map(role -> UUID.fromString((String) role.get("id")))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No role '" + roleName + "'"));
    }

    /** Signs in to a workspace and returns the access token; fails unless it answers 200. */
    protected String login(String subdomain, String email) {
        ResponseEntity<Map<String, Object>> response = loginResponse(subdomain, email, PASSWORD);
        assertThat(response.getStatusCode()).as("login: %s", response.getBody()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    /**
     * {@code POST /auth/login}, whatever it answers.
     *
     * @param subdomain the workspace header, or {@code null} for a header-less
     *                  login as the sign-in popup does it
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ResponseEntity<Map<String, Object>> loginResponse(String subdomain, String email, String password) {
        return (ResponseEntity) rest.postForEntity("/api/v1/auth/login",
                json(Map.of("email", email, "password", password), subdomain), Map.class);
    }

    private Member join(Workspace workspace, Map<String, Object> role, String emailPrefix) {
        String email = emailPrefix + "-" + UUID.randomUUID().toString().substring(0, 6)
                + "@" + workspace.subdomain() + ".io";
        Map<String, Object> body = new HashMap<>(role);
        body.put("firstName", "Bob");
        body.put("lastName", "Smith");
        body.put("email", email);
        ResponseEntity<Map<String, Object>> invited =
                call(HttpMethod.POST, "/api/v1/team/invitations", workspace.adminToken(), body);
        assertThat(invited.getStatusCode()).as("invite: %s", invited.getBody()).isEqualTo(HttpStatus.CREATED);

        rest.postForEntity("/api/v1/auth/accept-invite",
                json(Map.of("token", tokenIn(emailCapture.lastInviteLink()), "password", PASSWORD), null),
                String.class);
        emailCapture.clear();
        return new Member(UUID.fromString((String) invited.getBody().get("id")), email,
                login(workspace.subdomain(), email));
    }

    // ------------------------------------------------------------------- http

    /**
     * Calls the API and returns the JSON body as a map, whatever the status —
     * tests assert on the status themselves.
     *
     * @param token access token, or {@code null} for an anonymous call
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ResponseEntity<Map<String, Object>> call(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return (ResponseEntity) rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    /** {@code GET} of an endpoint that answers a JSON array. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected ResponseEntity<List<Map<String, Object>>> callForList(String path, String token) {
        return (ResponseEntity) rest.exchange(path, HttpMethod.GET, new HttpEntity<>(bearer(token)), List.class);
    }

    /** The status of a call, whatever shape its body has — for access checks. */
    protected HttpStatus statusOf(HttpMethod method, String path, String token) {
        HttpHeaders headers = token == null ? new HttpHeaders() : bearer(token);
        return HttpStatus.valueOf(rest.exchange(path, method, new HttpEntity<>(headers), String.class)
                .getStatusCode().value());
    }

    /** Anonymous {@code GET}, for public endpoints. */
    protected ResponseEntity<Map<String, Object>> get(String path) {
        return call(HttpMethod.GET, path, null, null);
    }

    protected static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    protected static HttpEntity<Map<String, Object>> json(Map<String, Object> body, String tenantSubdomain) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantSubdomain != null) {
            headers.set("X-Tenant-Subdomain", tenantSubdomain);
        }
        return new HttpEntity<>(body, headers);
    }

    protected static String tokenIn(String link) {
        return link.substring(link.indexOf("token=") + 6);
    }
}
