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
 * End-to-end tests for who sees which vacancy, through the real API against
 * a real PostgreSQL. A member whose role holds {@code jobs.view.all} sees the
 * whole company's vacancies. Everyone else sees only the ones they work on:
 * created, recruit for, manage, or are on the hiring team of. Every other
 * vacancy answers 404, as if it did not exist.
 */
@SuppressWarnings("unchecked")
class VacancyScopeIntegrationTest extends AbstractJobIntegrationTest {

    @Test
    void anInterviewer_seesAVacancyOnlyOnceTheyAreOnItsHiringTeam_andLosesItWhenTakenOff() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Object backend = create(acme.adminToken(), completeVacancy("Backend Engineer")).get("id");
        Object frontend = create(acme.adminToken(), completeVacancy("Frontend Engineer")).get("id");

        assertThat(listedTitles(interviewer.token(), "")).isEmpty();
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + backend, interviewer.token()))
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map<String, Object>> staffed = setTeam(acme.adminToken(), backend, interviewer.id());
        assertThat(staffed.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> members = (List<Map<String, Object>>) staffed.getBody().get("members");
        assertThat(members).singleElement().satisfies(member -> assertThat(member)
                .containsEntry("id", interviewer.id().toString())
                .containsEntry("roleName", "Interviewer")
                .containsEntry("status", "ACTIVE"));

        assertThat(listedTitles(interviewer.token(), "")).containsExactly("Backend Engineer");
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + backend, interviewer.token())).isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + backend + "/hiring-team", interviewer.token()))
                .isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + frontend, interviewer.token()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/counts", interviewer.token(), null).getBody())
                .containsEntry("DRAFT", 1);

        assertThat(setTeam(acme.adminToken(), backend).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + backend, interviewer.token()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aRoleThatMayManageButNotSeeEverything_worksOnlyOnItsOwnVacancies() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> role = call(HttpMethod.POST, "/api/v1/roles", acme.adminToken(),
                Map.of("name", "Junior Recruiter", "permissions", List.of("jobs.manage"))).getBody();
        Member junior = newMemberWithRole(acme, UUID.fromString((String) role.get("id")));
        Object theirs = create(junior.token(), completeVacancy("Their Vacancy")).get("id");
        Map<String, Object> others = create(acme.adminToken(), completeVacancy("Someone Else's"));

        assertThat(listedTitles(junior.token(), "")).containsExactly("Their Vacancy");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + theirs, junior.token(), null).getBody())
                .containsEntry("createdBy", junior.id().toString());

        // Someone else's vacancy is not there to edit: 404, not 403.
        Map<String, Object> edit = completeVacancy("Hijacked");
        edit.put("version", others.get("version"));
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + others.get("id"), junior.token(), edit).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(status(HttpMethod.POST, "/api/v1/jobs/" + others.get("id") + "/duplicate", junior.token()))
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void assignedToMe_andDepartment_narrowTheList() {
        Workspace acme = newWorkspace("Acme");
        Member recruiter = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        Map<String, Object> mine = completeVacancy("Mine");
        mine.put("assignedRecruiterId", recruiter.id().toString());
        create(acme.adminToken(), mine);
        Map<String, Object> sales = completeVacancy("Sales Lead");
        sales.put("department", "Sales");
        create(acme.adminToken(), sales);

        // A Recruiter sees every vacancy, and can narrow to their own or to one department.
        assertThat(listedTitles(recruiter.token(), "")).containsExactlyInAnyOrder("Mine", "Sales Lead");
        assertThat(listedTitles(recruiter.token(), "?assignedTo=me")).containsExactly("Mine");
        assertThat(listedTitles(recruiter.token(), "?department=sales")).containsExactly("Sales Lead");
        assertThat(listedTitles(acme.adminToken(), "?assignedTo=" + recruiter.id())).containsExactly("Mine");
        assertThat(status(HttpMethod.GET, "/api/v1/jobs?assignedTo=bob", recruiter.token()))
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void theHiringTeam_onlyTakesActiveMembersOfTheSameCompany_andFreezesOnArchive() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Member outsider = newMemberWithRole(globex, roleId(globex, "Interviewer"));
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Object vacancy = createPublished(acme.adminToken(), "Platform Engineer").get("id");

        ResponseEntity<Map<String, Object>> foreign = setTeam(acme.adminToken(), vacancy, outsider.id());
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(foreign.getBody()).containsEntry("code", "BUSINESS_RULE");

        assertThat(setTeam(acme.adminToken(), vacancy, interviewer.id()).getStatusCode()).isEqualTo(HttpStatus.OK);
        // Interviewers may see the team but not change it.
        assertThat(setTeam(interviewer.token(), vacancy).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        advance(acme.adminToken(), vacancy, "close", "archive");
        assertThat(setTeam(acme.adminToken(), vacancy).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aDuplicate_keepsTheHiringTeam() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Object source = create(acme.adminToken(), completeVacancy("Data Engineer")).get("id");
        setTeam(acme.adminToken(), source, interviewer.id());

        Object copy = call(HttpMethod.POST, "/api/v1/jobs/" + source + "/duplicate", acme.adminToken(), null)
                .getBody().get("id");

        List<Map<String, Object>> members = (List<Map<String, Object>>) call(HttpMethod.GET,
                "/api/v1/jobs/" + copy + "/hiring-team", acme.adminToken(), null).getBody().get("members");
        assertThat(members).extracting(member -> member.get("id")).containsExactly(interviewer.id().toString());
        // ...and so the interviewer sees the copy too.
        assertThat(status(HttpMethod.GET, "/api/v1/jobs/" + copy, interviewer.token())).isEqualTo(HttpStatus.OK);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<Map<String, Object>> setTeam(String token, Object vacancyId, UUID... members) {
        return call(HttpMethod.PUT, "/api/v1/jobs/" + vacancyId + "/hiring-team", token,
                Map.of("memberIds", List.of(members).stream().map(UUID::toString).toList()));
    }

    private List<Object> listedTitles(String token, String query) {
        ResponseEntity<Map<String, Object>> page = call(HttpMethod.GET, "/api/v1/jobs" + query, token, null);
        assertThat(page.getStatusCode()).as("list: %s", page.getBody()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .map(vacancy -> vacancy.get("title"))
                .toList();
    }

    private HttpStatus status(HttpMethod method, String path, String token) {
        return HttpStatus.valueOf(call(method, path, token, null).getStatusCode().value());
    }
}
