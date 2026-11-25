package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for interview panels (V25), through the real API against
 * a real PostgreSQL: a draft may be saved with a round short, publishing needs
 * every round fully staffed, anyone active in the company can sit on a panel
 * and then sees the vacancy, and none of it reaches the public board.
 */
@SuppressWarnings("unchecked")
class InterviewPanelIntegrationTest extends AbstractJobIntegrationTest {

    @Test
    void aDraftMayBeShort_butPublishingNeedsEveryRoundComplete() {
        Workspace acme = newWorkspace("Acme");
        Member ada = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Member grace = newMemberWithRole(acme, roleId(acme, "Recruiter"));
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("interviewPanels", null);
        Object vacancy = create(acme.adminToken(), body).get("id");

        List<Map<String, Object>> rounds = panelsOf(acme.adminToken(), vacancy).getBody();
        assertThat(rounds).isNotEmpty().allSatisfy(round -> assertThat(round.get("interviewerCount")).isNull());
        ResponseEntity<Map<String, Object>> refused = move(acme.adminToken(), vacancy, "publish");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) refused.getBody().get("message")).contains("needs its interviewers");

        // Two wanted, one chosen: fine on a draft, still not publishable.
        assertThat(setPanels(acme.adminToken(), vacancy, rounds, 2, List.of(ada.id())).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat((String) move(acme.adminToken(), vacancy, "publish").getBody().get("message"))
                .contains("needs 2 interviewers, 1 chosen");

        ResponseEntity<String> complete =
                setPanels(acme.adminToken(), vacancy, rounds, 2, List.of(ada.id(), grace.id()));
        assertThat(complete.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(move(acme.adminToken(), vacancy, "publish").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Live now: a panel can't be left short.
        assertThat(setPanels(acme.adminToken(), vacancy, rounds, 2, List.of(ada.id())).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void morePeopleThanTheCount_orSomeoneFromAnotherCompany_isRefused() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Member ours = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Member theirs = newMemberWithRole(globex, roleId(globex, "Interviewer"));
        Object vacancy = create(acme.adminToken(), completeVacancy("Backend Engineer")).get("id");
        List<Map<String, Object>> rounds = panelsOf(acme.adminToken(), vacancy).getBody();

        assertThat(setPanels(acme.adminToken(), vacancy, rounds, 1, List.of(ours.id(), theirs.id())).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        ResponseEntity<String> foreign = setPanels(acme.adminToken(), vacancy, rounds, 2,
                List.of(ours.id(), theirs.id()));
        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(foreign.getBody()).contains("active member of your workspace");
    }

    @Test
    void aPanelMember_seesTheVacancy_whateverTheirRole() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Object vacancy = create(acme.adminToken(), completeVacancy("Backend Engineer")).get("id");
        assertThat(statusOf(HttpMethod.GET, "/api/v1/jobs/" + vacancy, interviewer.token()))
                .isEqualTo(HttpStatus.NOT_FOUND);

        List<Map<String, Object>> rounds = panelsOf(acme.adminToken(), vacancy).getBody();
        setPanels(acme.adminToken(), vacancy, rounds, 1, List.of(interviewer.id()));

        assertThat(statusOf(HttpMethod.GET, "/api/v1/jobs/" + vacancy, interviewer.token())).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> seen = panelsOf(interviewer.token(), vacancy).getBody();
        assertThat((List<Map<String, Object>>) seen.get(0).get("interviewers"))
                .extracting(person -> person.get("id"))
                .containsExactly(interviewer.id().toString());
    }

    @Test
    void theMemberSearch_findsPeopleByTheirFirstLetters_inTheCallersCompanyOnly() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Member ours = newMemberWithRole(acme, roleId(acme, "Interviewer"));
        Member theirs = newMemberWithRole(globex, roleId(globex, "Interviewer"));

        String prefix = ours.email().substring(0, 3).toUpperCase();
        ResponseEntity<List<Map<String, Object>>> found =
                callForList("/api/v1/jobs/assignable-members?q=" + prefix, acme.adminToken());

        assertThat(found.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(found.getBody()).extracting(person -> person.get("id"))
                .contains(ours.id().toString())
                .doesNotContain(theirs.id().toString());
        assertThat(statusOf(HttpMethod.GET, "/api/v1/jobs/assignable-members?q=a", ours.token()))
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void theVacancysCreator_isNamed_soNoRosterIsNeeded() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> vacancy = create(acme.adminToken(), completeVacancy("Backend Engineer"));

        assertThat(vacancy.get("createdBy")).isNotNull();
        assertThat((String) vacancy.get("createdByName")).isNotBlank();
    }

    private ResponseEntity<String> setPanels(String token, Object vacancyId,
                                                          List<Map<String, Object>> rounds, int count,
                                                          List<?> people) {
        List<Map<String, Object>> panels = rounds.stream().map(round -> {
            Map<String, Object> panel = new HashMap<>();
            panel.put("stagePosition", round.get("position"));
            panel.put("interviewerCount", count);
            panel.put("interviewerIds", people.stream().map(Object::toString).toList());
            return panel;
        }).toList();
        // As text: the answer is the list of panels, a refusal is an error object.
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/jobs/" + vacancyId + "/interview-panels", HttpMethod.PUT,
                new HttpEntity<>(Map.of("panels", panels), headers), String.class);
    }
}
