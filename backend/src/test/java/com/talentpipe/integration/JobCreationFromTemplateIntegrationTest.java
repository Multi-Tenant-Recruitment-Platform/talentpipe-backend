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
 * The snapshot engine end to end, against real PostgreSQL: a job created from
 * a pipeline template gets its own copy of the template's stages and settings,
 * stored in the same transaction as the job, and nothing done to the template
 * afterwards — editing, archiving, deleting — reaches the job.
 *
 * <p>Everything goes through the HTTP API, as the dashboard does it. The only
 * direct SQL reads the stored rows back, and installs the temporary trigger
 * the atomicity test needs.</p>
 */
class JobCreationFromTemplateIntegrationTest extends AbstractJobIntegrationTest {

    private static final String TEMPLATES = "/api/v1/pipeline-templates";

    // ------------------------------------------------------------ inheritance

    @Test
    void aJobCreatedWithoutOverrides_takesTheTemplatesSettings_andAnExactCopyOfItsStages() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, true, standardStages()));

        Map<String, Object> job = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer")));

        assertThat(job)
                .containsEntry("pipelineTemplateId", template.get("id"))
                .containsEntry("pipelineTemplateName", "Engineering pipeline")
                .containsEntry("stageCount", 4)
                .containsEntry("interviewRoundCount", 1)
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("blindModeEnabled", true)
                .containsEntry("version", 0);

        List<Map<String, Object>> stages = stagesOf(acme.adminToken(), job.get("id")).getBody();
        assertThat(stages)
                .extracting("position", "stageType", "name", "benchmark")
                .containsExactly(
                        tuple(0, "POST_APPLICATION", "Applied", null),
                        tuple(1, "SCREENING", "Screening", null),
                        tuple(2, "INTERVIEW", "Technical interview", 75),
                        tuple(3, "ONBOARD", "Onboard", null));
        // Copies, not references: each stage is a row of the job's own.
        assertThat(stages).extracting("id").doesNotContainAnyElementsOf(stageIds(template));
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT tenant_id FROM job_stages WHERE job_id = ?", UUID.class, uuid(job.get("id"))))
                .containsExactly(acme.tenantId());
    }

    @Test
    void overrides_applyToTheJobAlone_theTemplateKeepsItsOwnDefaults() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, true, standardStages()));
        Map<String, Object> body = from(template, completeVacancy("Backend Engineer"));
        body.put("autoThreshold", 90);
        body.put("manualThreshold", 65);
        body.put("blindModeEnabled", false);

        Map<String, Object> job = create(acme.adminToken(), body);

        assertThat(job)
                .containsEntry("autoThreshold", 90)
                .containsEntry("manualThreshold", 65)
                .containsEntry("blindModeEnabled", false);
        assertThat(templateAsStored(acme, template))
                .containsEntry("defaultAutoThreshold", 80)
                .containsEntry("defaultManualThreshold", 70)
                .containsEntry("blindModeEnabled", true);
    }

    // ---------------------------------------------------------------- decoupling

    @Test
    void editingTheTemplate_leavesAnExistingJobOnItsOriginalFourStages() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Object jobId = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer"))).get("id");
        List<Map<String, Object>> before = stagesOf(acme.adminToken(), jobId).getBody();

        // The template gains a fifth stage, its interview is renamed and made harder, and its settings change.
        // Written to the template's own tables: replacing stages through PUT /pipeline-templates/{id}
        // currently fails on uq_pipeline_template_stages_pos (the new stages are inserted before the old
        // ones are deleted), and what is under test here is the job's copy, not the template editor.
        UUID templateId = uuid(template.get("id"));
        jdbc.update("UPDATE pipeline_template_stages SET position = 4 WHERE template_id = ? AND position = 3",
                templateId);
        jdbc.update("UPDATE pipeline_template_stages SET name = 'Coding interview', benchmark = 90 "
                + "WHERE template_id = ? AND position = 2", templateId);
        jdbc.update("INSERT INTO pipeline_template_stages (tenant_id, template_id, position, stage_type, name, benchmark) "
                + "VALUES (?, ?, 3, 'INTERVIEW', 'Culture interview', 60)", acme.tenantId(), templateId);
        jdbc.update("UPDATE pipeline_templates SET default_auto_threshold = 85, default_manual_threshold = 75, "
                + "blind_mode_enabled = true WHERE id = ?", templateId);
        assertThat(templateAsStored(acme, template)).containsEntry("stageCount", 5);

        assertThat(stagesOf(acme.adminToken(), jobId).getBody()).hasSize(4).isEqualTo(before);
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("stageCount", 4)
                .containsEntry("interviewRoundCount", 1)
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("blindModeEnabled", false);

        // The copy is taken when a job is created: a new job gets the template as it is now.
        Map<String, Object> newJob = create(acme.adminToken(), from(template, completeVacancy("Frontend Engineer")));
        assertThat(newJob).containsEntry("stageCount", 5).containsEntry("interviewRoundCount", 2);
        Object newJobId = newJob.get("id");
        assertThat(stagesOf(acme.adminToken(), newJobId).getBody())
                .extracting("name")
                .containsExactly("Applied", "Screening", "Coding interview", "Culture interview", "Onboard");
    }

    @Test
    void renamingTheTemplate_leavesAnExistingJobWithTheNameItWasCreatedUnder() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Object jobId = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer"))).get("id");

        // Written to the template's table for the reason given in the editing test above.
        jdbc.update("UPDATE pipeline_templates SET name = 'Platform pipeline' WHERE id = ?", uuid(template.get("id")));
        assertThat(templateAsStored(acme, template)).containsEntry("name", "Platform pipeline");

        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("pipelineTemplateId", template.get("id"))
                .containsEntry("pipelineTemplateName", "Engineering pipeline");
        assertThat(create(acme.adminToken(), from(template, completeVacancy("Frontend Engineer"))))
                .containsEntry("pipelineTemplateName", "Platform pipeline");
    }

    @Test
    void archivingTheTemplate_leavesExistingJobsAlone_butNoNewJobMayUseIt() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Object jobId = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer"))).get("id");
        List<Map<String, Object>> before = stagesOf(acme.adminToken(), jobId).getBody();

        ResponseEntity<Map<String, Object>> archived =
                call(HttpMethod.POST, TEMPLATES + "/" + template.get("id") + "/archive", acme.adminToken(), null);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(archived.getBody()).containsEntry("status", "ARCHIVED");

        assertThat(stagesOf(acme.adminToken(), jobId).getBody()).hasSize(4).isEqualTo(before);
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("pipelineTemplateId", template.get("id"));

        ResponseEntity<Map<String, Object>> refused =
                attemptCreate(acme.adminToken(), from(template, completeVacancy("Frontend Engineer")));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody()).containsEntry("message",
                "The selected pipeline template is archived and cannot be used for new jobs.");
        assertThat(jobsStoredFor(acme)).isEqualTo(1);
    }

    @Test
    void deletingTheTemplate_keepsTheJobAndItsStages_andOnlyClearsTheLink() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, true, standardStages()));
        Object jobId = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer"))).get("id");
        List<Map<String, Object>> before = stagesOf(acme.adminToken(), jobId).getBody();

        assertThat(call(HttpMethod.DELETE, TEMPLATES + "/" + template.get("id"), acme.adminToken(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("pipelineTemplateId", null)
                // What the job took from the template is its own and stays.
                .containsEntry("pipelineTemplateName", "Engineering pipeline")
                .containsEntry("stageCount", 4)
                .containsEntry("interviewRoundCount", 1)
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("blindModeEnabled", true);
        assertThat(stagesOf(acme.adminToken(), jobId).getBody()).isEqualTo(before);
    }

    // ------------------------------------------------ editing the pipeline settings

    @Test
    void editingTheThresholds_savesThemOnTheJob_andLeavesTheTemplateAlone() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Map<String, Object> body = from(template, completeVacancy("Backend Engineer"));
        Object jobId = create(acme.adminToken(), body).get("id");

        // The body still names the job's own template: sending it back unchanged is fine.
        body.put("version", 0);
        body.put("autoThreshold", 85);
        body.put("manualThreshold", 65);
        body.put("blindModeEnabled", true);
        ResponseEntity<Map<String, Object>> saved = call(HttpMethod.PUT, "/api/v1/jobs/" + jobId, acme.adminToken(), body);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("version", 1);
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("autoThreshold", 85)
                .containsEntry("manualThreshold", 65)
                .containsEntry("blindModeEnabled", true)
                .containsEntry("pipelineTemplateId", template.get("id"))
                .containsEntry("stageCount", 4);
        assertThat(templateAsStored(acme, template))
                .containsEntry("defaultAutoThreshold", 80)
                .containsEntry("defaultManualThreshold", 70)
                .containsEntry("blindModeEnabled", false);
    }

    @Test
    void namingAnotherTemplate_onAnEdit_isRefused_andTheJobIsUnchanged() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Map<String, Object> other = createTemplate(acme.adminToken(),
                templateBody("Sales pipeline", 90, 60, true, standardStages()));
        Map<String, Object> body = from(template, completeVacancy("Backend Engineer"));
        Object jobId = create(acme.adminToken(), body).get("id");
        List<Map<String, Object>> stagesBefore = stagesOf(acme.adminToken(), jobId).getBody();

        body.put("version", 0);
        body.put("pipelineTemplateId", other.get("id"));
        body.put("title", "Renamed on the way");
        ResponseEntity<Map<String, Object>> refused =
                call(HttpMethod.PUT, "/api/v1/jobs/" + jobId, acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "The pipeline template cannot be changed after a job is created.");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("pipelineTemplateId", template.get("id"))
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("title", "Backend Engineer")
                .containsEntry("version", 0);
        assertThat(stagesOf(acme.adminToken(), jobId).getBody()).isEqualTo(stagesBefore);
    }

    @Test
    void aJobWhoseTemplateWasDeleted_canStillChangeItsThresholds_butCannotNameATemplate() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Map<String, Object> body = from(template, completeVacancy("Backend Engineer"));
        Object jobId = create(acme.adminToken(), body).get("id");
        assertThat(call(HttpMethod.DELETE, TEMPLATES + "/" + template.get("id"), acme.adminToken(), null)
                .getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        // The frontend now holds pipelineTemplateId: null, and sends back what it holds.
        Map<String, Object> edit = completeVacancy("Backend Engineer");
        edit.put("version", 0);
        edit.put("pipelineTemplateId", null);
        edit.put("autoThreshold", 85);
        edit.put("manualThreshold", 65);
        ResponseEntity<Map<String, Object>> saved =
                call(HttpMethod.PUT, "/api/v1/jobs/" + jobId, acme.adminToken(), edit);
        assertThat(saved.getStatusCode()).as("edit: %s", saved.getBody()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody())
                .containsEntry("autoThreshold", 85)
                .containsEntry("manualThreshold", 65)
                .containsEntry("pipelineTemplateId", null)
                .containsEntry("pipelineTemplateName", "Engineering pipeline");

        // The deleted template's id is no longer the job's: sending it is a change.
        edit.put("version", 1);
        edit.put("pipelineTemplateId", template.get("id"));
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + jobId, acme.adminToken(), edit).getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void aSingleThresholdThatBreaksTheOrderAgainstTheStoredOne_isRefused_andNothingChanges() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Engineering pipeline", 80, 70, false, standardStages()));
        Object jobId = create(acme.adminToken(), from(template, completeVacancy("Backend Engineer"))).get("id");

        Map<String, Object> edit = completeVacancy("Backend Engineer");
        edit.put("version", 0);
        edit.put("manualThreshold", 85); // above the stored auto threshold of 80
        ResponseEntity<Map<String, Object>> refused =
                call(HttpMethod.PUT, "/api/v1/jobs/" + jobId, acme.adminToken(), edit);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "The manual threshold must be lower than the auto threshold.");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + jobId, acme.adminToken(), null).getBody())
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("version", 0);
    }

    // ------------------------------------------------------------------ refusals

    @Test
    void aJobWithoutATemplate_isABadRequest_andNothingIsStored() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("pipelineTemplateId", null);

        ResponseEntity<Map<String, Object>> refused = attemptCreate(acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat((String) refused.getBody().get("message")).contains("Pipeline template is required.");
        assertThat(jobsStoredFor(acme)).isZero();
    }

    @Test
    void anotherCompanysTemplate_isNotFound_exactlyLikeOneThatDoesNotExist() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Map<String, Object> globexTemplate = createTemplate(globex.adminToken(),
                templateBody("Globex pipeline", 80, 70, false, standardStages()));

        ResponseEntity<Map<String, Object>> foreign =
                attemptCreate(acme.adminToken(), from(globexTemplate, completeVacancy("Backend Engineer")));
        Map<String, Object> unknownBody = completeVacancy("Backend Engineer");
        unknownBody.put("pipelineTemplateId", UUID.randomUUID().toString());
        ResponseEntity<Map<String, Object>> unknown = attemptCreate(acme.adminToken(), unknownBody);

        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getBody().get("message")).isEqualTo(unknown.getBody().get("message"));
        assertThat(jobsStoredFor(acme)).isZero();
    }

    @Test
    void thresholdOverridesThatEndUpOutOfOrder_areRefused_andNothingIsStored() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Backend Engineer");
        body.put("manualThreshold", 85); // above the standard template's auto threshold of 80

        ResponseEntity<Map<String, Object>> refused = attemptCreate(acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "The manual threshold must be lower than the auto threshold.");
        assertThat(jobsStoredFor(acme)).isZero();
    }

    @Test
    void aJobsStages_areHiddenFromOtherCompanies_andFromTeamMembersNotOnTheJob() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        String interviewer = newMember(acme, "INTERVIEWER");
        Object jobId = create(acme.adminToken(), completeVacancy("Backend Engineer")).get("id");
        String path = "/api/v1/jobs/" + jobId + "/stages";

        assertThat(statusOf(HttpMethod.GET, path, acme.adminToken())).isEqualTo(HttpStatus.OK);
        assertThat(statusOf(HttpMethod.GET, path, globex.adminToken())).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, path, interviewer)).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ------------------------------------------------------------------ atomicity

    @Test
    void creatingAJob_isAllOrNothing_aStageTheDatabaseRefusesLeavesNoJobBehind() {
        Workspace acme = newWorkspace("Acme");
        List<Map<String, Object>> stages = standardStages();
        stages.set(2, stage(2, "INTERVIEW", REFUSED_STAGE, 75));
        Map<String, Object> template = createTemplate(acme.adminToken(),
                templateBody("Doomed pipeline", 80, 70, false, stages));

        ResponseEntity<Map<String, Object>> refused;
        installStageRefusal();
        try {
            refused = attemptCreate(acme.adminToken(), from(template, completeVacancy("Never stored")));
        } finally {
            removeStageRefusal();
        }

        // The vacancy row was inserted before the stages; the failed stage took it back out.
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(jobsStoredFor(acme)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM job_stages WHERE tenant_id = ?",
                Long.class, acme.tenantId())).isZero();
    }

    /** A stage name the temporary trigger below refuses, so a stage insert fails after the job's own. */
    private static final String REFUSED_STAGE = "Refused by the database";

    private void installStageRefusal() {
        removeStageRefusal();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION test_refuse_job_stage() RETURNS trigger AS $$
                BEGIN
                    IF NEW.name = '%s' THEN
                        RAISE EXCEPTION 'stage refused for the atomicity test' USING ERRCODE = 'check_violation';
                    END IF;
                    RETURN NEW;
                END
                $$ LANGUAGE plpgsql
                """.formatted(REFUSED_STAGE));
        jdbc.execute("""
                CREATE TRIGGER test_refuse_job_stage BEFORE INSERT ON job_stages
                    FOR EACH ROW EXECUTE FUNCTION test_refuse_job_stage()
                """);
    }

    private void removeStageRefusal() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_refuse_job_stage ON job_stages");
        jdbc.execute("DROP FUNCTION IF EXISTS test_refuse_job_stage()");
    }

    // ------------------------------------------------------------------ helpers

    /** The vacancy body, created from the given template. */
    private static Map<String, Object> from(Map<String, Object> template, Map<String, Object> body) {
        body.put("pipelineTemplateId", template.get("id"));
        return body;
    }

    private Map<String, Object> templateAsStored(Workspace workspace, Map<String, Object> template) {
        ResponseEntity<Map<String, Object>> stored =
                call(HttpMethod.GET, TEMPLATES + "/" + template.get("id"), workspace.adminToken(), null);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        return stored.getBody();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> stageIds(Map<String, Object> template) {
        return ((List<Map<String, Object>>) template.get("stages")).stream().map(stage -> stage.get("id")).toList();
    }

    private long jobsStoredFor(Workspace workspace) {
        return jdbc.queryForObject("SELECT count(*) FROM job_vacancies WHERE tenant_id = ?",
                Long.class, workspace.tenantId());
    }

    private static UUID uuid(Object id) {
        return UUID.fromString((String) id);
    }
}
