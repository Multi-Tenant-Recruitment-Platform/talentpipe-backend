package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Who sees and moves a company's applicants, and who edits its pipeline
 * templates, through the real API against a real PostgreSQL.
 *
 * <p>Both used to be decided by the old fixed role names, so an Interviewer
 * could read and reject every applicant in the company, a custom role given
 * "Move applicants" could not, and an Owner's edits to the Recruiter role
 * changed nothing. Now they follow the role's permissions (ADR-9), and a
 * member without {@code applications.view.all} sees only the applicants to
 * vacancies they work on (ADR-11).
 */
@SuppressWarnings("unchecked")
class ApplicantAccessIntegrationTest extends AbstractJobIntegrationTest {

    private static final String APPLICANTS = "/api/v1/applications/tenant";

    // ------------------------------------------------------------ applicants

    @Test
    void anInterviewer_seesOnlyTheApplicantsOfTheirVacancies_andMovesNone() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Object backend = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object frontend = createPublished(acme.adminToken(), "Frontend Engineer").get("id");
        String candidate = newCandidate();
        Object toBackend = apply(candidate, backend);
        Object toFrontend = apply(candidate, frontend);

        assertThat(jobIdsListed(interviewer.token())).isEmpty();

        putOnHiringTeam(acme.adminToken(), backend, interviewer.id());
        assertThat(jobIdsListed(interviewer.token())).containsExactly(backend);

        // Not even on their own vacancy: moving applicants is not an interviewer's call.
        assertThat(moveApplicant(interviewer.token(), toBackend, "REJECTED")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(moveApplicant(interviewer.token(), toFrontend, "REJECTED")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(toBackend, acme.adminToken())).isEqualTo("APPLIED");
    }

    @Test
    void theOwnerAndARecruiter_seeAndMoveEveryApplicant() {
        Workspace acme = newWorkspace("Acme");
        Member recruiter = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        Object backend = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object frontend = createPublished(acme.adminToken(), "Frontend Engineer").get("id");
        String candidate = newCandidate();
        Object toBackend = apply(candidate, backend);
        apply(candidate, frontend);

        assertThat(jobIdsListed(acme.adminToken())).containsExactlyInAnyOrder(backend, frontend);
        assertThat(jobIdsListed(recruiter.token())).containsExactlyInAnyOrder(backend, frontend);
        assertThat(moveApplicant(recruiter.token(), toBackend, "SHORTLISTED")).isEqualTo(HttpStatus.OK);
        assertThat(moveApplicant(acme.adminToken(), toBackend, "IN_REVIEW")).isEqualTo(HttpStatus.OK);
    }

    @Test
    void aCustomRoleThatMovesApplicants_movesThoseOfItsOwnVacancies_andCannotFindTheRest() {
        Workspace acme = newWorkspace("Acme");
        Member junior = newMemberWithRole(acme, customRole(acme, "Junior Recruiter",
                "jobs.manage", "jobs.publish", "applications.manage"));
        Object theirs = createPublished(junior.token(), "Their Vacancy").get("id");
        Object others = createPublished(acme.adminToken(), "Someone Else's").get("id");
        String candidate = newCandidate();
        Object toTheirs = apply(candidate, theirs);
        Object toOthers = apply(candidate, others);

        assertThat(jobIdsListed(junior.token())).containsExactly(theirs);
        assertThat(moveApplicant(junior.token(), toTheirs, "SHORTLISTED")).isEqualTo(HttpStatus.OK);
        // Someone else's applicant is not there to move: 404, not 403, and nothing changes.
        assertThat(moveApplicant(junior.token(), toOthers, "REJECTED")).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(toOthers, acme.adminToken())).isEqualTo("APPLIED");
    }

    @Test
    void aCustomRoleThatSeesEveryApplicant_seesThemAll_butMovesNone() {
        Workspace acme = newWorkspace("Acme");
        Member coordinator = newMemberWithRole(acme, customRole(acme, "Hiring Coordinator",
                "applications.view.all"));
        Object backend = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object frontend = createPublished(acme.adminToken(), "Frontend Engineer").get("id");
        String candidate = newCandidate();
        Object toBackend = apply(candidate, backend);
        apply(candidate, frontend);

        assertThat(jobIdsListed(coordinator.token())).containsExactlyInAnyOrder(backend, frontend);
        assertThat(moveApplicant(coordinator.token(), toBackend, "REJECTED")).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void takingMoveApplicantsOffTheRecruiterRole_stopsItAtOnce() {
        Workspace acme = newWorkspace("Acme");
        Member recruiter = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        Object backend = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object application = apply(newCandidate(), backend);
        assertThat(moveApplicant(recruiter.token(), application, "IN_REVIEW")).isEqualTo(HttpStatus.OK);

        editRole(acme, "Recruiter", List.of("jobs.view.all", "jobs.manage", "jobs.publish",
                "applications.view.all", "interviews.schedule"));

        // Same token, still a "Recruiter" by name, still HR_MANAGER underneath.
        assertThat(moveApplicant(recruiter.token(), application, "REJECTED")).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(application, acme.adminToken())).isEqualTo("IN_REVIEW");
    }

    @Test
    void anotherCompanysApplicants_stayInvisible() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Object backend = createPublished(acme.adminToken(), "Backend Engineer").get("id");
        Object application = apply(newCandidate(), backend);

        assertThat(jobIdsListed(globex.adminToken())).isEmpty();
        assertThat(moveApplicant(globex.adminToken(), application, "REJECTED")).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------- templates

    @Test
    void aRecruiter_readsTemplatesToCreateVacancies_butChangesNone() {
        Workspace acme = newWorkspace("Acme");
        Member recruiter = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        UUID template = standardTemplate(acme);

        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates", recruiter.token()))
                .isEqualTo(HttpStatus.OK);
        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates/" + template, recruiter.token()))
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.POST, "/api/v1/pipeline-templates", recruiter.token(),
                templateBody("Recruiter's Own", 80, 60, false, standardStages())).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(HttpMethod.POST, "/api/v1/pipeline-templates/" + template + "/duplicate",
                recruiter.token())).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(HttpMethod.DELETE, "/api/v1/pipeline-templates/" + template, recruiter.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aCustomRoleThatManagesTemplates_createsAndEditsThem() {
        Workspace acme = newWorkspace("Acme");
        Member designer = newMemberWithRole(acme, customRole(acme, "Process Designer",
                "pipeline.templates.manage"));

        ResponseEntity<Map<String, Object>> created = call(HttpMethod.POST, "/api/v1/pipeline-templates",
                designer.token(), templateBody("Designer's Pipeline", 80, 60, false, standardStages()));
        assertThat(created.getStatusCode()).as("create: %s", created.getBody()).isEqualTo(HttpStatus.CREATED);
        Object id = created.getBody().get("id");

        assertThat(statusOf(HttpMethod.POST, "/api/v1/pipeline-templates/" + id + "/duplicate", designer.token()))
                .isEqualTo(HttpStatus.CREATED);
        assertThat(statusOf(HttpMethod.POST, "/api/v1/pipeline-templates/" + id + "/archive", designer.token()))
                .isEqualTo(HttpStatus.OK);
        // A template manager also sees the archived ones.
        Map<String, Object> archived = call(HttpMethod.GET, "/api/v1/pipeline-templates?includeArchived=true",
                designer.token(), null).getBody();
        assertThat((List<Map<String, Object>>) archived.get("content"))
                .extracting(template -> template.get("id")).contains(id);
    }

    @Test
    void takingVacancyEditingOffTheRecruiterRole_closesTheTemplatesToo() {
        Workspace acme = newWorkspace("Acme");
        Member recruiter = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        standardTemplate(acme);
        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates", recruiter.token()))
                .isEqualTo(HttpStatus.OK);

        editRole(acme, "Recruiter", List.of("jobs.view.all", "applications.view.all"));

        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates", recruiter.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void anInterviewer_neverReachesTheTemplates() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        UUID template = standardTemplate(acme);

        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates", interviewer.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(statusOf(HttpMethod.GET, "/api/v1/pipeline-templates/" + template, interviewer.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    // --------------------------------------------------------------- helpers

    /** Registers a candidate, verifies their email and signs them in. */
    private String newCandidate() {
        String email = "applicant-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        call(HttpMethod.POST, "/api/v1/public/candidates/register", null, Map.of(
                "fullName", "Nimal Silva",
                "identityCardNumber", "200012345678",
                "address", "Kandy",
                "contactNumber", "+94 77 123 4567",
                "email", email,
                "password", PASSWORD));
        rest.postForEntity("/api/v1/auth/verify-email",
                json(Map.of("token", tokenIn(emailCapture.lastVerificationLink())), null), String.class);
        emailCapture.clear();
        ResponseEntity<Map<String, Object>> login = loginResponse(null, email, PASSWORD);
        assertThat(login.getStatusCode()).as("login: %s", login.getBody()).isEqualTo(HttpStatus.OK);
        return (String) login.getBody().get("accessToken");
    }

    private Object apply(String candidate, Object vacancyId) {
        ResponseEntity<Map<String, Object>> response =
                call(HttpMethod.POST, "/api/v1/applications/jobs/" + vacancyId, candidate, null);
        assertThat(response.getStatusCode()).as("apply: %s", response.getBody()).isEqualTo(HttpStatus.CREATED);
        return response.getBody().get("id");
    }

    /** The vacancy of every applicant the member's list shows; fails unless the list answers 200. */
    private List<Object> jobIdsListed(String token) {
        ResponseEntity<List<Map<String, Object>>> listed = callForList(APPLICANTS, token);
        assertThat(listed.getStatusCode()).as("list applicants").isEqualTo(HttpStatus.OK);
        return listed.getBody().stream().map(application -> application.get("jobId")).toList();
    }

    private HttpStatus moveApplicant(String token, Object applicationId, String status) {
        return HttpStatus.valueOf(call(HttpMethod.PATCH, "/api/v1/applications/" + applicationId + "/status",
                token, Map.of("status", status)).getStatusCode().value());
    }

    /** The applicant's status as the Owner sees it. */
    private Object statusOf(Object applicationId, String ownerToken) {
        return callForList(APPLICANTS, ownerToken).getBody().stream()
                .filter(application -> applicationId.equals(application.get("id")))
                .map(application -> application.get("status"))
                .findFirst()
                .orElseThrow();
    }

    private void putOnHiringTeam(String token, Object vacancyId, UUID member) {
        ResponseEntity<Map<String, Object>> staffed = call(HttpMethod.PUT, "/api/v1/jobs/" + vacancyId
                + "/hiring-team", token, Map.of("memberIds", List.of(member.toString())));
        assertThat(staffed.getStatusCode()).as("hiring team: %s", staffed.getBody()).isEqualTo(HttpStatus.OK);
    }

    private UUID customRole(Workspace workspace, String name, String... permissions) {
        ResponseEntity<Map<String, Object>> role = call(HttpMethod.POST, "/api/v1/roles", workspace.adminToken(),
                Map.of("name", name, "permissions", List.of(permissions)));
        assertThat(role.getStatusCode()).as("role: %s", role.getBody()).isEqualTo(HttpStatus.CREATED);
        return UUID.fromString((String) role.getBody().get("id"));
    }

    /** Gives a role exactly these permissions, as the Owner does in the role editor. */
    private void editRole(Workspace workspace, String roleName, List<String> permissions) {
        UUID id = roleId(workspace, roleName);
        Map<String, Object> current = call(HttpMethod.GET, "/api/v1/roles/" + id, workspace.adminToken(), null)
                .getBody();
        ResponseEntity<Map<String, Object>> edited = call(HttpMethod.PUT, "/api/v1/roles/" + id,
                workspace.adminToken(), Map.of("name", roleName, "permissions", new ArrayList<>(permissions),
                        "version", current.get("version")));
        assertThat(edited.getStatusCode()).as("edit role: %s", edited.getBody()).isEqualTo(HttpStatus.OK);
    }
}
