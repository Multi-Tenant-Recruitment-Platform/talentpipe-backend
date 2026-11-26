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
 * End-to-end tests for ideal candidate matching (V27), through the real API
 * against a real PostgreSQL: the profile is saved with a create, a draft may be
 * short of 100%, publishing needs exactly 100%, names are unique ignoring case,
 * a template that does not offer matching refuses it, and screening is handed
 * the weights as fractions. None of it reaches the public board.
 */
@SuppressWarnings("unchecked")
class IdealMatchingIntegrationTest extends AbstractJobIntegrationTest {

    @Test
    void aDraftMayBeShortOfAHundred_butPublishingNeedsExactlyAHundred() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("pipelineTemplateId", matchingTemplate(acme));
        body.put("toolsAndTechnologies", List.of("Docker", "Git"));
        body.put("idealMatching", profile(
                criterion("TECHNICAL_SKILL", "Java", 50, "MUST_HAVE"),
                criterion("TOOL", "Docker", 35, "PREFERRED")));
        Map<String, Object> vacancy = create(acme.adminToken(), body);
        Object id = vacancy.get("id");
        assertThat(vacancy).containsEntry("idealMatchingAvailable", true).containsEntry("idealMatchingEnabled", true);

        Map<String, Object> stored = call(HttpMethod.GET, "/api/v1/jobs/" + id + "/ideal-matching",
                acme.adminToken(), null).getBody();
        assertThat(stored).containsEntry("totalWeightPercent", 85);
        assertThat((List<Map<String, Object>>) stored.get("criteria"))
                .extracting(criterion -> criterion.get("name"))
                .containsExactly("Java", "Docker");

        ResponseEntity<Map<String, Object>> refused = move(acme.adminToken(), id, "publish");
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) refused.getBody().get("message")).contains("add up to 85%");

        ResponseEntity<String> fixed = putProfile(acme.adminToken(), id, profile(
                criterion("TECHNICAL_SKILL", "Java", 50, "MUST_HAVE"),
                criterion("TOOL", "Docker", 30, "PREFERRED"),
                criterion("EXPERIENCE", null, 20, null)));
        assertThat(fixed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(move(acme.adminToken(), id, "publish").getStatusCode()).isEqualTo(HttpStatus.OK);

        // Live now: the weights must stay at 100%.
        assertThat(putProfile(acme.adminToken(), id, profile(criterion("TECHNICAL_SKILL", "Java", 50, "MUST_HAVE")))
                .getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // The public advert shows the tools, never the profile.
        Map<String, Object> advert = get("/api/v1/public/jobs/" + id).getBody();
        assertThat(advert).containsEntry("toolsAndTechnologies", List.of("Docker", "Git"))
                .doesNotContainKeys("idealMatchingEnabled", "idealMatchingAvailable", "criteria");
    }

    @Test
    void theScreeningProfile_handsOverTheWeightsAsFractions_withTheMustHaves() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("pipelineTemplateId", matchingTemplate(acme));
        body.put("education", "Bachelor’s degree");
        body.put("idealMatching", profileWith(List.of(
                criterion("TECHNICAL_SKILL", "Java", 60, "MUST_HAVE"),
                criterion("TOOL", "Docker", 40, "PREFERRED")),
                List.of("Computer Science"), List.of("AWS Solutions Architect")));
        Object id = create(acme.adminToken(), body).get("id");

        Map<String, Object> profile = call(HttpMethod.GET, "/api/v1/jobs/" + id + "/screening-profile",
                acme.adminToken(), null).getBody();

        assertThat(profile).containsEntry("enabled", true);
        assertThat((List<Map<String, Object>>) profile.get("criteriaWeights"))
                .extracting(weight -> weight.get("name"), weight -> weight.get("weight"))
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Java", 0.6),
                        org.assertj.core.groups.Tuple.tuple("Docker", 0.4));
        assertThat(profile).containsEntry("mustHave", List.of("Java"))
                .containsEntry("acceptedDegreeTypes", List.of("Computer Science"))
                .containsEntry("niceToHaveCertifications", List.of("AWS Solutions Architect"));
    }

    @Test
    void theSameNameTwice_ignoringCase_isRefused() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("pipelineTemplateId", matchingTemplate(acme));
        body.put("idealMatching", profile(
                criterion("TECHNICAL_SKILL", "Git", 50, "MUST_HAVE"),
                criterion("TOOL", "git", 50, "PREFERRED")));

        ResponseEntity<Map<String, Object>> refused = attemptCreate(acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) refused.getBody().get("message")).contains("listed twice");
    }

    @Test
    void aTemplateThatDoesNotOfferMatching_refusesIt() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("idealMatching", profile(criterion("TECHNICAL_SKILL", "Java", 100, "MUST_HAVE")));

        ResponseEntity<Map<String, Object>> refused = attemptCreate(acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) refused.getBody().get("message")).contains("does not offer ideal candidate matching");
    }

    @Test
    void anEditWithoutAProfile_keepsTheStoredOne() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("pipelineTemplateId", matchingTemplate(acme));
        body.put("idealMatching", profile(criterion("TECHNICAL_SKILL", "Java", 100, "MUST_HAVE")));
        Map<String, Object> vacancy = create(acme.adminToken(), body);
        Object id = vacancy.get("id");

        Map<String, Object> edit = new HashMap<>(completeVacancy("Backend Engineer II"));
        edit.put("version", vacancy.get("version"));
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), edit).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        Map<String, Object> stored = call(HttpMethod.GET, "/api/v1/jobs/" + id + "/ideal-matching",
                acme.adminToken(), null).getBody();
        assertThat(stored).containsEntry("enabled", true).containsEntry("totalWeightPercent", 100);
    }

    // ---------------------------------------------------------------- helpers

    /** A template that offers ideal candidate matching. */
    private String matchingTemplate(Workspace workspace) {
        Map<String, Object> template = templateBody("Matching hiring", 80, 70, false, standardStages());
        template.put("idealMatchingEnabled", true);
        return (String) createTemplate(workspace.adminToken(), template).get("id");
    }

    private static Map<String, Object> profile(Map<String, Object>... criteria) {
        return profileWith(List.of(criteria), List.of(), List.of());
    }

    private static Map<String, Object> profileWith(List<Map<String, Object>> criteria, List<String> degreeTypes,
                                                   List<String> certifications) {
        Map<String, Object> profile = new HashMap<>();
        profile.put("enabled", true);
        profile.put("criteria", criteria);
        profile.put("degreeTypes", degreeTypes);
        profile.put("niceToHaveCertifications", certifications);
        return profile;
    }

    private static Map<String, Object> criterion(String category, String name, Integer weight, String requirement) {
        Map<String, Object> criterion = new HashMap<>();
        criterion.put("category", category);
        criterion.put("name", name);
        criterion.put("weightPercent", weight);
        criterion.put("requirement", requirement);
        return criterion;
    }

    private ResponseEntity<String> putProfile(String token, Object vacancyId, Map<String, Object> profile) {
        HttpHeaders headers = bearer(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange("/api/v1/jobs/" + vacancyId + "/ideal-matching", HttpMethod.PUT,
                new HttpEntity<>(profile, headers), String.class);
    }
}
