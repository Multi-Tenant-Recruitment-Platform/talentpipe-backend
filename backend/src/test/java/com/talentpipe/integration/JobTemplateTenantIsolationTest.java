package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Pipeline templates and the stages copied from them never cross a company
 * boundary, against real PostgreSQL: one company can neither create a job from
 * another's template, nor read another's template, job or stages, nor edit
 * another's job — and every refusal looks exactly like the one for an id that
 * does not exist, so a refusal never confirms that something exists elsewhere.
 *
 * <p>Two companies, each with its own template and a job created from it.
 * Every request goes through the HTTP API as the dashboard sends it; direct SQL
 * only reads rows back.</p>
 */
class JobTemplateTenantIsolationTest extends AbstractJobIntegrationTest {

    private static final String TEMPLATES = "/api/v1/pipeline-templates";
    private static final String JOBS = "/api/v1/jobs";

    private Workspace acme;
    private Workspace globex;
    private Map<String, Object> acmeTemplate;
    private Map<String, Object> globexTemplate;
    private Object acmeJob;
    private Object globexJob;

    @BeforeEach
    void twoCompaniesEachWithATemplateAndAJob() {
        acme = newWorkspace("Acme");
        globex = newWorkspace("Globex");
        acmeTemplate = createTemplate(acme.adminToken(),
                templateBody("Acme engineering", 80, 70, false, standardStages()));
        globexTemplate = createTemplate(globex.adminToken(),
                templateBody("Globex engineering", 90, 60, true, standardStages()));
        acmeJob = create(acme.adminToken(), withTemplate(acmeTemplate.get("id"), "Acme backend")).get("id");
        globexJob = create(globex.adminToken(), withTemplate(globexTemplate.get("id"), "Globex backend")).get("id");
    }

    // ------------------------------------------------------------ creating a job

    @Test
    void creatingAJobFromAnotherCompanysTemplate_isNotFound_exactlyLikeAnUnknownTemplate() {
        ResponseEntity<Map<String, Object>> foreign =
                attemptCreate(acme.adminToken(), withTemplate(globexTemplate.get("id"), "Stolen pipeline"));
        ResponseEntity<Map<String, Object>> unknown =
                attemptCreate(acme.adminToken(), withTemplate(UUID.randomUUID().toString(), "No pipeline"));

        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(foreign.getBody().get("message")).isEqualTo(unknown.getBody().get("message"));
        assertThat(jobsOf(acme)).isOne();
        assertThat(templateAsStored(globex, globexTemplate))
                .containsEntry("name", "Globex engineering")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void creatingAJobFromAnotherCompanysArchivedTemplate_isNotFound_notRefusedForBeingArchived() {
        // A 422 "archived" would confirm the template exists in the other company.
        archive(globex, globexTemplate);

        ResponseEntity<Map<String, Object>> foreign =
                attemptCreate(acme.adminToken(), withTemplate(globexTemplate.get("id"), "Stolen pipeline"));

        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(jobsOf(acme)).isOne();
    }

    // ------------------------------------------------------------ reading

    @Test
    void anotherCompanysTemplate_cannotBeRead_norSeenInTheList() {
        assertThat(statusOf(HttpMethod.GET, TEMPLATES + "/" + globexTemplate.get("id"), acme.adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(templateIdsListedFor(acme))
                .contains(acmeTemplate.get("id"))
                .doesNotContain(globexTemplate.get("id"));
    }

    @Test
    void anotherCompanysJob_andItsStages_areNotFound() {
        assertThat(statusOf(HttpMethod.GET, JOBS + "/" + globexJob, acme.adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(statusOf(HttpMethod.GET, JOBS + "/" + globexJob + "/stages", acme.adminToken()))
                .isEqualTo(HttpStatus.NOT_FOUND);
        // The owner still reads them.
        assertThat(stagesOf(globex.adminToken(), globexJob).getBody()).hasSize(4);
    }

    @Test
    void aJobsCopiedStages_belongOnlyToItsOwnCompany() {
        assertThat(jdbc.queryForList("SELECT DISTINCT tenant_id FROM job_stages WHERE job_id = ?",
                UUID.class, uuid(acmeJob))).containsExactly(acme.tenantId());
        assertThat(jdbc.queryForList("SELECT DISTINCT tenant_id FROM job_stages WHERE job_id = ?",
                UUID.class, uuid(globexJob))).containsExactly(globex.tenantId());
    }

    // ------------------------------------------------------------ editing

    @Test
    void namingAnotherCompanysTemplate_onAnEdit_isRefusedExactlyLikeAnyOtherTemplate() {
        ResponseEntity<Map<String, Object>> foreign = edit(acme, acmeJob, globexTemplate.get("id"));
        ResponseEntity<Map<String, Object>> unknown = edit(acme, acmeJob, UUID.randomUUID().toString());

        assertThat(foreign.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(foreign.getBody())
                .containsEntry("message", "The pipeline template cannot be changed after a job is created.");
        assertThat(foreign.getBody().get("message")).isEqualTo(unknown.getBody().get("message"));
        assertThat(call(HttpMethod.GET, JOBS + "/" + acmeJob, acme.adminToken(), null).getBody())
                .containsEntry("pipelineTemplateId", acmeTemplate.get("id"))
                .containsEntry("autoThreshold", 80)
                .containsEntry("manualThreshold", 70)
                .containsEntry("version", 0);
    }

    @Test
    void editingAnotherCompanysJob_isNotFound_andTheJobIsUnchanged() {
        Map<String, Object> body = completeVacancy("Hijacked title");
        body.put("version", 0);
        body.put("autoThreshold", 10);
        body.put("manualThreshold", 5);

        ResponseEntity<Map<String, Object>> refused =
                call(HttpMethod.PUT, JOBS + "/" + globexJob, acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.GET, JOBS + "/" + globexJob, globex.adminToken(), null).getBody())
                .containsEntry("title", "Globex backend")
                .containsEntry("autoThreshold", 90)
                .containsEntry("manualThreshold", 60)
                .containsEntry("version", 0);
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, Object> withTemplate(Object templateId, String title) {
        Map<String, Object> body = completeVacancy(title);
        body.put("pipelineTemplateId", templateId);
        return body;
    }

    /** An edit of a job that names a template, with new thresholds the refusal must not let through. */
    private ResponseEntity<Map<String, Object>> edit(Workspace workspace, Object jobId, Object templateId) {
        Map<String, Object> body = withTemplate(templateId, "Edited title");
        body.put("version", 0);
        body.put("autoThreshold", 85);
        body.put("manualThreshold", 65);
        return call(HttpMethod.PUT, JOBS + "/" + jobId, workspace.adminToken(), body);
    }

    private void archive(Workspace workspace, Map<String, Object> template) {
        assertThat(call(HttpMethod.POST, TEMPLATES + "/" + template.get("id") + "/archive",
                workspace.adminToken(), null).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private Map<String, Object> templateAsStored(Workspace workspace, Map<String, Object> template) {
        ResponseEntity<Map<String, Object>> stored =
                call(HttpMethod.GET, TEMPLATES + "/" + template.get("id"), workspace.adminToken(), null);
        assertThat(stored.getStatusCode()).isEqualTo(HttpStatus.OK);
        return stored.getBody();
    }

    @SuppressWarnings("unchecked")
    private List<Object> templateIdsListedFor(Workspace workspace) {
        ResponseEntity<Map<String, Object>> page =
                call(HttpMethod.GET, TEMPLATES + "?size=100&includeArchived=true", workspace.adminToken(), null);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((List<Map<String, Object>>) page.getBody().get("content")).stream()
                .map(template -> template.get("id"))
                .toList();
    }

    private long jobsOf(Workspace workspace) {
        return jdbc.queryForObject("SELECT count(*) FROM job_vacancies WHERE tenant_id = ?",
                Long.class, workspace.tenantId());
    }

    private static UUID uuid(Object id) {
        return UUID.fromString((String) id);
    }
}
