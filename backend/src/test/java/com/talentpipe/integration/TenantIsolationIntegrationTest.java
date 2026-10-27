package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.talentpipe.auth.entity.User;
import com.talentpipe.auth.repository.RoleAuditEventRepository;
import com.talentpipe.auth.repository.TenantRoleRepository;
import com.talentpipe.auth.repository.UserRepository;
import com.talentpipe.common.tenant.TenantContext;
import com.talentpipe.integration.TestEmailConfig.CapturingEmailService;
import com.talentpipe.job.repository.JobVacancyRepository;
import com.talentpipe.job.repository.VacancyTeamMemberRepository;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Tenant data isolation (Sprint 2): a company admin must never be able to see
 * or act on another company's data through any endpoint.
 *
 * <p>Contract under test, per the story's acceptance criteria:</p>
 * <ul>
 *   <li>All queries are scoped by the caller's tenant id (taken from the
 *       verified JWT — never from the request).</li>
 *   <li>Attempts to touch another tenant's resources are denied. The platform
 *       convention is <strong>404, not 403</strong>: a resource in another
 *       tenant must be indistinguishable from one that does not exist.</li>
 * </ul>
 *
 * <p>Enforcement is layered — the Hibernate tenant filter (ORM level) plus the
 * explicit scope checks in the services — and this test intentionally goes
 * through the public HTTP API only, so it holds whichever layer catches it.</p>
 */
@Import(TestEmailConfig.class)
class TenantIsolationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CapturingEmailService emailCapture;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TenantRoleRepository tenantRoleRepository;

    @Autowired
    private RoleAuditEventRepository roleAuditEventRepository;

    @Autowired
    private JobVacancyRepository jobVacancyRepository;

    @Autowired
    private VacancyTeamMemberRepository vacancyTeamMemberRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearEmailCapture() {
        emailCapture.clear();
    }

    // ------------------------------------------------------------- helpers

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Registers a company, verifies its admin's email, and returns an access token. */
    private String registerVerifyAndLogin(String subdomain, String email) {
        Map<String, Object> body = Map.of(
                "companyName", "Corp " + subdomain,
                "subdomain", subdomain,
                "admin", Map.of(
                        "firstName", "Admin",
                        "lastName", "Of " + subdomain,
                        "email", email,
                        "password", "s3cret-password"));
        ResponseEntity<String> registered = rest.postForEntity(
                "/api/v1/auth/register", jsonEntity(body, null), String.class);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String link = emailCapture.lastVerificationLink();
        rest.postForEntity("/api/v1/auth/verify-email",
                jsonEntity(Map.of("token", link.substring(link.indexOf("token=") + 6)), null),
                String.class);

        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", email, "password", "s3cret-password"), subdomain),
                Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    /** Invites a teammate and returns the created INVITED user's id. */
    private String invite(String accessToken, String email) {
        ResponseEntity<Map> response = rest.exchange("/api/v1/team/invitations", HttpMethod.POST,
                jsonEntity(Map.of(
                        "firstName", "Invited",
                        "lastName", "Person",
                        "email", email,
                        "role", "HR_MANAGER"), null, accessToken),
                Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    /** One company's rows in each of the newer tenant-scoped tables. */
    private record CompanyRows(UUID tenantId, UUID roleId, UUID vacancyId) {
    }

    /**
     * Gives the company signed in as {@code accessToken} a custom role (which
     * also writes a role audit event), a vacancy, and a hiring team on it.
     */
    private CompanyRows companyWithRoleVacancyAndTeam(String accessToken) {
        ResponseEntity<Map> me = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                jsonEntity(null, null, accessToken), Map.class);
        UUID tenantId = UUID.fromString((String) me.getBody().get("tenantId"));
        String memberId = (String) me.getBody().get("id");

        Map<String, Object> roleBody = new HashMap<>();
        roleBody.put("name", unique("Coordinator"));
        roleBody.put("description", null);
        roleBody.put("permissions", List.of("team.view"));
        ResponseEntity<Map> role = rest.exchange("/api/v1/roles", HttpMethod.POST,
                jsonEntity(roleBody, null, accessToken), Map.class);
        assertThat(role.getStatusCode()).as("role: %s", role.getBody()).isEqualTo(HttpStatus.CREATED);

        String vacancyId = createVacancy(accessToken, "DRAFT");
        ResponseEntity<Map> team = rest.exchange("/api/v1/jobs/" + vacancyId + "/hiring-team", HttpMethod.PUT,
                jsonEntity(Map.of("memberIds", List.of(memberId)), null, accessToken), Map.class);
        assertThat(team.getStatusCode()).as("team: %s", team.getBody()).isEqualTo(HttpStatus.OK);

        return new CompanyRows(tenantId, UUID.fromString((String) role.getBody().get("id")),
                UUID.fromString(vacancyId));
    }

    /** Creates a complete vacancy in the given status and returns its id. */
    private String createVacancy(String accessToken, String status) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", "Platform Engineer");
        body.put("department", "Engineering");
        body.put("openings", 1);
        body.put("employmentType", "FULL_TIME");
        body.put("workplaceType", "HYBRID");
        body.put("location", "Colombo, Sri Lanka");
        body.put("applicationDeadline", LocalDate.now().plusDays(30).toString());
        body.put("jobSummary", "Keep the hiring platform running.");
        body.put("jobDescription", "You will look after the services behind the job board.");
        body.put("keyResponsibilities", List.of("Run the services"));
        body.put("requiredSkills", List.of("Java"));
        body.put("status", status);
        ResponseEntity<Map> created = rest.exchange("/api/v1/jobs", HttpMethod.POST,
                jsonEntity(body, null, accessToken), Map.class);
        assertThat(created.getStatusCode()).as("vacancy: %s", created.getBody()).isEqualTo(HttpStatus.CREATED);
        return (String) created.getBody().get("id");
    }

    private ResponseEntity<List> listTeam(String accessToken) {
        return rest.exchange("/api/v1/team", HttpMethod.GET,
                jsonEntity(null, null, accessToken), List.class);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body,
                                                              String tenantSubdomain) {
        return jsonEntity(body, tenantSubdomain, null);
    }

    private static HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body,
                                                              String tenantSubdomain,
                                                              String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantSubdomain != null) {
            headers.set("X-Tenant-Subdomain", tenantSubdomain);
        }
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return new HttpEntity<>(body, headers);
    }

    // --------------------------------------------------------------- tests

    @Test
    void signingInToAnotherCompany_works_whileTheBrowserStillHoldsAToken() {
        // The SPA attaches whatever token it holds to every request, sign-in
        // included. Sign-in looks the account up across companies, so the
        // filter must stay off there whatever token comes along.
        String tokenA = registerVerifyAndLogin(unique("iso-k"), "admin-k@iso-test.io");
        String subdomainB = unique("iso-l");
        registerVerifyAndLogin(subdomainB, "admin-l@iso-test.io");

        ResponseEntity<Map> login = rest.postForEntity("/api/v1/auth/login",
                jsonEntity(Map.of("email", "admin-l@iso-test.io", "password", "s3cret-password"),
                        subdomainB, tokenA),
                Map.class);

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void ormFilter_alsoScopesRolesAuditEventsVacanciesAndHiringTeams() {
        // The same service-bypassing check as the one for users, for the
        // tenant-scoped tables added after the filter: company roles, the role
        // audit trail, vacancies and vacancy hiring teams.
        CompanyRows a = companyWithRoleVacancyAndTeam(
                registerVerifyAndLogin(unique("iso-m"), "admin-m@iso-test.io"));
        CompanyRows b = companyWithRoleVacancyAndTeam(
                registerVerifyAndLogin(unique("iso-n"), "admin-n@iso-test.io"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TenantContext.set(a.tenantId());
            try {
                assertThat(tenantRoleRepository.findAll()).isNotEmpty()
                        .allSatisfy(role -> assertThat(role.getTenantId()).isEqualTo(a.tenantId()));
                assertThat(tenantRoleRepository.findById(b.roleId())).isEmpty();

                assertThat(roleAuditEventRepository.findAll()).isNotEmpty()
                        .allSatisfy(event -> assertThat(event.getTenantId()).isEqualTo(a.tenantId()));

                assertThat(jobVacancyRepository.findAll()).isNotEmpty()
                        .allSatisfy(vacancy -> assertThat(vacancy.getTenantId()).isEqualTo(a.tenantId()));
                assertThat(jobVacancyRepository.findById(b.vacancyId())).isEmpty();

                assertThat(vacancyTeamMemberRepository.findAll()).isNotEmpty()
                        .allSatisfy(member -> assertThat(member.getTenantId()).isEqualTo(a.tenantId()));
            } finally {
                TenantContext.clear();
            }
        });

        // Without a tenant in context the other company's rows are reachable again.
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThat(tenantRoleRepository.findById(b.roleId())).isPresent();
            assertThat(jobVacancyRepository.findById(b.vacancyId())).isPresent();
        });
    }

    @Test
    void publicJobBoard_showsEveryCompany_toASignedInMemberOfOne() {
        // A recruiter who leaves the dashboard for the public board still
        // sends their token. The board spans companies, so another company's
        // advert must open for them exactly as it does for a visitor.
        String tokenA = registerVerifyAndLogin(unique("iso-p"), "admin-p@iso-test.io");
        String tokenB = registerVerifyAndLogin(unique("iso-q"), "admin-q@iso-test.io");
        String vacancyB = createVacancy(tokenB, "PUBLISHED");

        ResponseEntity<Map> advert = rest.exchange("/api/v1/public/jobs/" + vacancyB, HttpMethod.GET,
                jsonEntity(null, null, tokenA), Map.class);

        assertThat(advert.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void teamListing_isScopedToTheCallersTenant() {
        String tokenA = registerVerifyAndLogin(unique("iso-a"), "admin-a@iso-test.io");
        String tokenB = registerVerifyAndLogin(unique("iso-b"), "admin-b@iso-test.io");

        invite(tokenA, "hr-a@iso-test.io");
        invite(tokenB, "hr-b@iso-test.io");

        // Each admin sees exactly their own workspace — the other tenant's
        // members and invitations must be completely absent, not just hidden.
        List<Map<String, Object>> teamA = listTeam(tokenA).getBody();
        List<Map<String, Object>> teamB = listTeam(tokenB).getBody();

        assertThat(teamA)
                .extracting(member -> member.get("email"))
                .contains("admin-a@iso-test.io", "hr-a@iso-test.io")
                .doesNotContain("admin-b@iso-test.io", "hr-b@iso-test.io");
        assertThat(teamB)
                .extracting(member -> member.get("email"))
                .contains("admin-b@iso-test.io", "hr-b@iso-test.io")
                .doesNotContain("admin-a@iso-test.io", "hr-a@iso-test.io");
    }

    @Test
    void actingOnAnotherTenantsInvitation_returns404_neverConfirmingItExists() {
        String tokenA = registerVerifyAndLogin(unique("iso-c"), "admin-c@iso-test.io");
        String tokenB = registerVerifyAndLogin(unique("iso-d"), "admin-d@iso-test.io");

        String invitedByA = invite(tokenA, "hr-c@iso-test.io");

        // Admin B tries to re-send and revoke A's invitation by its real id.
        // Both must come back 404 — the platform never answers 403 here,
        // because 403 would confirm the resource exists in another tenant.
        ResponseEntity<Map> resend = rest.exchange(
                "/api/v1/team/invitations/" + invitedByA + "/resend", HttpMethod.POST,
                jsonEntity(null, null, tokenB), Map.class);
        assertThat(resend.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> revoke = rest.exchange(
                "/api/v1/team/invitations/" + invitedByA, HttpMethod.DELETE,
                jsonEntity(null, null, tokenB), Map.class);
        assertThat(revoke.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(revoke.getBody().get("status")).isEqualTo(404);

        // And the failed cross-tenant attempts must not have touched the real
        // invitation: its owner still sees it, and can still revoke it.
        List<Map<String, Object>> teamA = listTeam(tokenA).getBody();
        assertThat(teamA)
                .extracting(member -> member.get("email"))
                .contains("hr-c@iso-test.io");
        ResponseEntity<Map> ownerRevoke = rest.exchange(
                "/api/v1/team/invitations/" + invitedByA, HttpMethod.DELETE,
                jsonEntity(null, null, tokenA), Map.class);
        assertThat(ownerRevoke.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void ormFilter_scopesEveryQuery_evenWithoutServiceLevelChecks() {
        // This test deliberately bypasses the service layer and its explicit
        // scope checks, going straight at the repository the way a future
        // (buggy) service might. With a tenant bound to TenantContext inside a
        // transaction, the Hibernate filter alone must hide the other tenant's
        // rows — from listing queries AND from findById. This is the layer that
        // makes "all queries are scoped" true by default rather than by
        // discipline.
        String tokenA = registerVerifyAndLogin(unique("iso-g"), "admin-g@iso-test.io");
        registerVerifyAndLogin(unique("iso-h"), "admin-h@iso-test.io");

        ResponseEntity<Map> meA = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                jsonEntity(null, null, tokenA), Map.class);
        UUID tenantAId = UUID.fromString((String) meA.getBody().get("tenantId"));

        UUID userBId = new TransactionTemplate(transactionManager).execute(status ->
                userRepository.findAllByEmail("admin-h@iso-test.io").get(0).getId());

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TenantContext.set(tenantAId);
            try {
                // Unscoped findAll: the filter must reduce it to tenant A only.
                List<User> visible = userRepository.findAll();
                assertThat(visible)
                        .isNotEmpty()
                        .allSatisfy(user -> assertThat(user.getTenantId()).isEqualTo(tenantAId));

                // Load-by-key of another tenant's row: invisible, not found.
                assertThat(userRepository.findById(userBId)).isEmpty();
            } finally {
                TenantContext.clear();
            }
        });

        // Outside any tenant context the same row is reachable again — the
        // filter scopes tenant requests, it does not damage global flows
        // (login, password reset) that legitimately search across tenants.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                assertThat(userRepository.findById(userBId)).isPresent());
    }

    @Test
    void sameEmailInTwoTenants_staysTwoIsolatedAccounts() {
        // Users are unique per (tenant, email): the same address may exist in
        // both companies, and each login must resolve to its own tenant's
        // account — never the other's.
        String subdomainA = unique("iso-e");
        String subdomainB = unique("iso-f");
        String shared = "shared@iso-test.io";
        String tokenA = registerVerifyAndLogin(subdomainA, shared);
        String tokenB = registerVerifyAndLogin(subdomainB, shared);

        ResponseEntity<Map> meA = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                jsonEntity(null, null, tokenA), Map.class);
        ResponseEntity<Map> meB = rest.exchange("/api/v1/auth/me", HttpMethod.GET,
                jsonEntity(null, null, tokenB), Map.class);

        assertThat(meA.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meA.getBody().get("tenantId")).isNotEqualTo(meB.getBody().get("tenantId"));
        assertThat(meA.getBody().get("tenantName")).isEqualTo("Corp " + subdomainA);
        assertThat(meB.getBody().get("tenantName")).isEqualTo("Corp " + subdomainB);
    }
}
