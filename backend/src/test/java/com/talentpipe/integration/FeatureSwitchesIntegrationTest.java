package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for the dashboard's switches, saved on the server: the
 * company's (blind screening, and what a new vacancy starts with) and each
 * vacancy's own (AI screening, candidate matching — PB-015).
 */
class FeatureSwitchesIntegrationTest extends AbstractJobIntegrationTest {

    private static final String SETTINGS = "/api/v1/tenant/settings";

    @Test
    void companySwitches_startOff_andChangeOneAtATime() {
        Workspace acme = newWorkspace("Acme");
        assertThat(call(HttpMethod.GET, SETTINGS, acme.adminToken(), null).getBody())
                .containsEntry("blindScreeningEnabled", false)
                .containsEntry("aiScreeningDefault", false)
                .containsEntry("candidateMatchingDefault", false);

        call(HttpMethod.PATCH, SETTINGS, acme.adminToken(), Map.of("blindScreeningEnabled", true));
        ResponseEntity<Map<String, Object>> second =
                call(HttpMethod.PATCH, SETTINGS, acme.adminToken(), Map.of("aiScreeningDefault", true));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(second.getBody())
                .containsEntry("blindScreeningEnabled", true)
                .containsEntry("aiScreeningDefault", true)
                .containsEntry("candidateMatchingDefault", false);
    }

    @Test
    void companySwitches_areReadableByEveryone_butChangedOnlyWithSettingsEdit() {
        Workspace acme = newWorkspace("Acme");
        Member interviewer = newMemberWithRole(acme, roleId(acme, "Interviewer"));

        assertThat(call(HttpMethod.GET, SETTINGS, interviewer.token(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(call(HttpMethod.PATCH, SETTINGS, interviewer.token(), Map.of("blindScreeningEnabled", true))
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aNewVacancy_startsWithTheCompanyDefaults_andKeepsItsOwnSwitchesAfterThat() {
        Workspace acme = newWorkspace("Acme");
        call(HttpMethod.PATCH, SETTINGS, acme.adminToken(), Map.of("candidateMatchingDefault", true));

        Map<String, Object> vacancy = create(acme.adminToken(), completeVacancy("Data Engineer"));
        assertThat(vacancy).containsEntry("aiScreeningEnabled", false)
                .containsEntry("candidateMatchingEnabled", true);

        // Changing the default later leaves existing vacancies alone.
        call(HttpMethod.PATCH, SETTINGS, acme.adminToken(), Map.of("candidateMatchingDefault", false));
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + vacancy.get("id"), acme.adminToken(), null).getBody())
                .containsEntry("candidateMatchingEnabled", true);

        // An edit that does not mention the switches keeps them too.
        Map<String, Object> edit = completeVacancy("Data Engineer II");
        edit.put("version", vacancy.get("version"));
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + vacancy.get("id"), acme.adminToken(), edit).getBody())
                .containsEntry("candidateMatchingEnabled", true);
    }

    @Test
    void aVacancysSwitches_flipOneAtATime_whileItIsOpen() {
        Workspace acme = newWorkspace("Acme");
        Object vacancyId = createPublished(acme.adminToken(), "Platform Engineer").get("id");
        String features = "/api/v1/jobs/" + vacancyId + "/features";

        ResponseEntity<Map<String, Object>> on =
                call(HttpMethod.PATCH, features, acme.adminToken(), Map.of("aiScreeningEnabled", true));
        assertThat(on.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(on.getBody()).containsEntry("aiScreeningEnabled", true)
                .containsEntry("candidateMatchingEnabled", false);

        advance(acme.adminToken(), vacancyId, "close");
        ResponseEntity<Map<String, Object>> closed =
                call(HttpMethod.PATCH, features, acme.adminToken(), Map.of("aiScreeningEnabled", false));
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(closed.getBody()).containsEntry("code", "BUSINESS_RULE");
    }
}
