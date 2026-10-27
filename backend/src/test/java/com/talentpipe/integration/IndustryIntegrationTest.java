package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * End-to-end tests for industries: a company picks a main industry and up to
 * three more from the fixed list, each of its vacancies is filed under one of
 * them, and the public board can be filtered by industry.
 */
@SuppressWarnings("unchecked")
class IndustryIntegrationTest extends AbstractJobIntegrationTest {

    @Test
    void aCompanysIndustries_areSavedInTheirListedSpelling_andShownOnItsProfile() {
        Workspace acme = newWorkspace("Acme");

        ResponseEntity<Map<String, Object>> saved = saveIndustries(acme, "information technology",
                List.of("finance and banking", "Information Technology"));

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("industry", "Information Technology")
                .containsEntry("additionalIndustries", List.of("Finance & Banking"));
        assertThat(get("/api/v1/public/companies/" + acme.subdomain()).getBody())
                .containsEntry("additionalIndustries", List.of("Finance & Banking"));
    }

    @Test
    void anIndustryOffTheList_orTooManyOfThem_isRejected() {
        Workspace acme = newWorkspace("Acme");

        assertThat(saveIndustries(acme, "Space Tourism", List.of()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(saveIndustries(acme, "Education",
                List.of("Healthcare", "Manufacturing", "Other", "Finance & Banking")).getBody())
                .containsEntry("code", "VALIDATION_FAILED");
    }

    @Test
    void aVacancy_takesTheMainIndustry_orAnotherOfTheCompanys_butNoOther() {
        Workspace acme = newWorkspace("Acme");
        saveIndustries(acme, "Information Technology", List.of("Finance & Banking"));

        assertThat(create(acme.adminToken(), completeVacancy("Backend Engineer")))
                .containsEntry("industry", "Information Technology");

        Map<String, Object> fintech = completeVacancy("Payments Engineer");
        fintech.put("industry", "finance & banking");
        assertThat(create(acme.adminToken(), fintech)).containsEntry("industry", "Finance & Banking");

        Map<String, Object> nurse = completeVacancy("Nurse");
        nurse.put("industry", "Healthcare");
        ResponseEntity<Map<String, Object>> refused = call(HttpMethod.POST, "/api/v1/jobs", acme.adminToken(), nurse);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody()).containsEntry("message",
                "Choose one of your company's industries: Information Technology, Finance & Banking.");
    }

    @Test
    void aCompanyWithoutAnIndustry_canStillPublish_underNone() {
        Workspace acme = newWorkspace("Acme");

        Map<String, Object> published = createPublished(acme.adminToken(), "Generalist");

        assertThat(published).containsEntry("status", "PUBLISHED").containsEntry("industry", null);
    }

    @Test
    void theBoard_filtersByIndustry_andOffersEachIndustryInUse() {
        Workspace acme = newWorkspace("Acme");
        saveIndustries(acme, "Information Technology", List.of("Finance & Banking"));
        createPublished(acme.adminToken(), "Backend Engineer");
        Map<String, Object> fintech = completeVacancy("Payments Engineer");
        fintech.put("industry", "Finance & Banking");
        fintech.put("status", "PUBLISHED");
        create(acme.adminToken(), fintech);

        List<Map<String, Object>> cards = (List<Map<String, Object>>)
                get("/api/v1/public/jobs?industry=finance and banking").getBody().get("content");
        assertThat(cards).extracting(card -> card.get("title")).containsExactly("Payments Engineer");
        assertThat(cards.get(0)).containsEntry("industry", "Finance & Banking");

        assertThat((List<Map<String, Object>>) get("/api/v1/public/jobs/filters").getBody().get("industries"))
                .extracting(option -> option.get("value"))
                .containsExactlyInAnyOrder("Information Technology", "Finance & Banking");
        assertThat(get("/api/v1/public/jobs?industry=Space Tourism").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<Map<String, Object>> saveIndustries(Workspace workspace, String industry,
                                                               List<String> additional) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", workspace.companyName());
        body.put("email", "hello@" + workspace.subdomain() + ".io");
        body.put("industry", industry);
        body.put("additionalIndustries", additional);
        return call(HttpMethod.PATCH, "/api/v1/tenant", workspace.adminToken(), body);
    }
}
