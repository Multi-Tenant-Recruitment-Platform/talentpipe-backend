package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.integration.TestEmailConfig.CapturingEmailService;
import java.time.LocalDate;
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
 * Shared plumbing for the job module's integration tests: getting a signed-in
 * recruiter into a fresh workspace, and calling the vacancy API as them.
 *
 * <p>Everything goes through the real HTTP API — registration, email
 * verification, login, invitations — so a test's vacancies belong to a tenant
 * created the same way a customer's would be. The database is emptied before
 * each test by {@link AbstractIntegrationTest}; {@code job_vacancies} is
 * covered by that because it cascades from {@code tenants}.</p>
 */
@Import(TestEmailConfig.class)
abstract class AbstractJobIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "s3cret-password";

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

    /** A registered company and its first admin, signed in. */
    protected record Workspace(String subdomain, String companyName, UUID tenantId, String adminToken) {
    }

    // ------------------------------------------------------------ workspaces

    /** Registers a company, verifies its admin's email, and signs the admin in. */
    @SuppressWarnings("unchecked")
    protected Workspace newWorkspace(String companyName) {
        String subdomain = "jobs-" + UUID.randomUUID().toString().substring(0, 8);
        String email = "admin@" + subdomain + ".io";

        rest.postForEntity("/api/v1/auth/register", json(Map.of(
                "companyName", companyName,
                "subdomain", subdomain,
                "admin", Map.of("firstName", "Ada", "lastName", "Lovelace",
                        "email", email, "password", PASSWORD)), null), String.class);
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();

        String token = login(subdomain, email, PASSWORD);
        Map<String, Object> profile = call(HttpMethod.GET, "/api/v1/tenant", token, null).getBody();
        return new Workspace(subdomain, companyName, UUID.fromString((String) profile.get("id")), token);
    }

    /**
     * Invites a team member into the workspace, accepts the invitation and
     * signs them in.
     *
     * @param role {@code HR_MANAGER} or {@code INTERVIEWER}
     * @return the new member's access token
     */
    protected String newMember(Workspace workspace, String role) {
        String email = role.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 6)
                + "@" + workspace.subdomain() + ".io";
        ResponseEntity<Map<String, Object>> invited = call(HttpMethod.POST, "/api/v1/team/invitations",
                workspace.adminToken(),
                Map.of("firstName", "Bob", "lastName", "Smith", "email", email, "role", role));
        assertThat(invited.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        rest.postForEntity("/api/v1/auth/accept-invite",
                json(Map.of("token", tokenIn(emailCapture.lastInviteLink()), "password", PASSWORD), null),
                String.class);
        emailCapture.clear();
        return login(workspace.subdomain(), email, PASSWORD);
    }

    /** The id of the first user in the workspace with the given role. */
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

    @SuppressWarnings("unchecked")
    private String login(String subdomain, String email, String password) {
        ResponseEntity<Map> response = rest.postForEntity("/api/v1/auth/login",
                json(Map.of("email", email, "password", password), subdomain), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) response.getBody().get("accessToken");
    }

    // -------------------------------------------------------------- vacancies

    /**
     * A vacancy body that satisfies every publish rule, as a mutable map so a
     * test can change or remove exactly the field it is about. The deadline is
     * 30 days out, so it is valid whenever the suite runs.
     */
    protected static Map<String, Object> completeVacancy(String title) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("department", "Engineering");
        body.put("openings", 2);
        body.put("employmentType", "FULL_TIME");
        body.put("workplaceType", "HYBRID");
        body.put("location", "Colombo, Sri Lanka");
        body.put("applicationDeadline", LocalDate.now().plusDays(30).toString());
        body.put("jobSummary", "Lead the services behind our candidate pipeline.");
        body.put("jobDescription", "You will own the matching service.\n\nWe value pragmatic engineering.");
        body.put("keyResponsibilities", List.of("Own the matching service"));
        body.put("requiredSkills", List.of("Java", "Spring Boot"));
        body.put("preferredSkills", List.of("Kubernetes"));
        body.put("minimumExperienceYears", 4);
        body.put("certifications", List.of());
        body.put("languageRequirements", List.of("English"));
        body.put("benefits", List.of("HEALTH_INSURANCE"));
        body.put("workingDays", List.of("MON", "TUE", "WED", "THU", "FRI"));
        body.put("workingHours", "9:00 AM – 6:00 PM");
        body.put("shiftType", "DAY");
        body.put("expectedHoursPerWeek", 40);
        body.put("recruitmentPipelineId", "TECHNICAL");
        body.put("screeningQuestions", List.of("Do you have the right to work in Sri Lanka?"));
        body.put("status", "DRAFT");
        return body;
    }

    /** Creates a vacancy and returns the stored response; fails the test unless it answers 201. */
    protected Map<String, Object> create(String token, Map<String, Object> body) {
        ResponseEntity<Map<String, Object>> response = call(HttpMethod.POST, "/api/v1/jobs", token, body);
        assertThat(response.getStatusCode()).as("create: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /** Creates a complete vacancy already PUBLISHED. */
    protected Map<String, Object> createPublished(String token, String title) {
        Map<String, Object> body = completeVacancy(title);
        body.put("status", "PUBLISHED");
        return create(token, body);
    }

    /** {@code POST /jobs/{id}/{move}} — publish, close, archive or duplicate. */
    protected ResponseEntity<Map<String, Object>> move(String token, Object vacancyId, String move) {
        return call(HttpMethod.POST, "/api/v1/jobs/" + vacancyId + "/" + move, token, null);
    }

    /** Walks a vacancy forward through the given moves, asserting each succeeds. */
    protected void advance(String token, Object vacancyId, String... moves) {
        for (String move : moves) {
            assertThat(move(token, vacancyId, move).getStatusCode()).as(move).isEqualTo(HttpStatus.OK);
        }
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

    /** Anonymous {@code GET}, for the public job board. */
    protected ResponseEntity<Map<String, Object>> get(String path) {
        return call(HttpMethod.GET, path, null, null);
    }

    private static HttpHeaders bearer(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    private static HttpEntity<Map<String, Object>> json(Map<String, Object> body, String tenantSubdomain) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantSubdomain != null) {
            headers.set("X-Tenant-Subdomain", tenantSubdomain);
        }
        return new HttpEntity<>(body, headers);
    }

    private static String tokenIn(String link) {
        return link.substring(link.indexOf("token=") + 6);
    }
}
