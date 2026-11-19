package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Editing a pipeline template through {@code PUT /pipeline-templates/{id}},
 * against real PostgreSQL (BUG-01).
 *
 * <p>Every edit replaces the whole stage list, so the new stages reuse the
 * positions the old ones held. The table is unique on (template, position):
 * the old rows must be gone before the new ones are written, or every save —
 * a rename included — collides with itself and answers 409. A mocked service
 * cannot see that; only the database can.</p>
 */
class PipelineTemplateEditingIntegrationTest extends AbstractWorkspaceIntegrationTest {

    private static final String TEMPLATES = "/api/v1/pipeline-templates";

    @Test
    void renamingATemplate_withItsStagesUnchanged_isSaved() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));

        ResponseEntity<Map<String, Object>> saved = put(acme, template,
                templateBody("Platform pipeline", 80, 70, false, standardStages()));

        assertThat(saved.getStatusCode()).as("edit: %s", saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("name", "Platform pipeline").containsEntry("stageCount", 4);
        assertThat(stagesOf(saved.getBody())).extracting("position", "stageType", "name").containsExactly(
                tuple(0, "POST_APPLICATION", "Applied"),
                tuple(1, "SCREENING", "Screening"),
                tuple(2, "INTERVIEW", "Technical interview"),
                tuple(3, "ONBOARD", "Onboard"));
    }

    @Test
    void addingAnInterviewRound_movesOnboardDown_andIsSaved() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        List<Map<String, Object>> stages = standardStages();
        stages.set(3, stage(3, "INTERVIEW", "Culture interview", 60));
        stages.add(stage(4, "ONBOARD", "Onboard", null));

        ResponseEntity<Map<String, Object>> saved = put(acme, template,
                templateBody("Engineering pipeline", 85, 75, true, stages));

        assertThat(saved.getStatusCode()).as("edit: %s", saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody())
                .containsEntry("stageCount", 5)
                .containsEntry("interviewRoundCount", 2)
                .containsEntry("defaultAutoThreshold", 85)
                .containsEntry("blindModeEnabled", true);
        // Read back from the database, not the response: exactly the new stages, nothing left over.
        assertThat(jdbc.queryForList("SELECT position, name FROM pipeline_template_stages WHERE template_id = ? "
                + "ORDER BY position", uuid(template.get("id"))))
                .extracting(row -> row.get("position"), row -> row.get("name"))
                .containsExactly(
                        tuple(0, "Applied"), tuple(1, "Screening"), tuple(2, "Technical interview"),
                        tuple(3, "Culture interview"), tuple(4, "Onboard"));
    }

    @Test
    void removingAndReorderingRounds_isSaved_andCanBeEditedAgain() {
        Workspace acme = newWorkspace("Acme");
        List<Map<String, Object>> three = List.of(
                stage(0, "POST_APPLICATION", "Applied", null),
                stage(1, "INTERVIEW", "Technical interview", 75),
                stage(2, "INTERVIEW", "Panel interview", 70),
                stage(3, "INTERVIEW", "Culture interview", 60),
                stage(4, "ONBOARD", "Onboard", null));
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, new java.util.ArrayList<>(three)));

        // Culture first, panel dropped.
        ResponseEntity<Map<String, Object>> first = put(acme, template, templateBody("Engineering pipeline", 80, 70,
                false, new java.util.ArrayList<>(List.of(
                        stage(0, "POST_APPLICATION", "Applied", null),
                        stage(1, "INTERVIEW", "Culture interview", 60),
                        stage(2, "INTERVIEW", "Technical interview", 75),
                        stage(3, "ONBOARD", "Onboard", null)))));
        assertThat(first.getStatusCode()).as("first edit: %s", first.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(stagesOf(first.getBody())).extracting("name")
                .containsExactly("Applied", "Culture interview", "Technical interview", "Onboard");

        // A second save of the same template works too.
        ResponseEntity<Map<String, Object>> second = put(acme, template,
                templateBody("Engineering pipeline v2", 80, 70, false, standardStages()));
        assertThat(second.getStatusCode()).as("second edit: %s", second.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pipeline_template_stages WHERE template_id = ?",
                Long.class, uuid(template.get("id")))).isEqualTo(4);
    }

    @Test
    void aRealConflict_isStillAConflict_anotherActiveTemplateHasTheName() {
        Workspace acme = newWorkspace("Acme");
        createTemplate(acme.adminToken(), templateBody("Sales pipeline", 80, 70, false, standardStages()));
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));

        ResponseEntity<Map<String, Object>> clash = put(acme, template,
                templateBody("Sales pipeline", 80, 70, false, standardStages()));

        assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    // ------------------------------------------------------------------ helpers

    private ResponseEntity<Map<String, Object>> put(Workspace workspace, Map<String, Object> template,
                                                    Map<String, Object> body) {
        return call(HttpMethod.PUT, TEMPLATES + "/" + template.get("id"), workspace.adminToken(), body);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stagesOf(Map<String, Object> template) {
        return (List<Map<String, Object>>) template.get("stages");
    }

    private static UUID uuid(Object id) {
        return UUID.fromString((String) id);
    }
}
