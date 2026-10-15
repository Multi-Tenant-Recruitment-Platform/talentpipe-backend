package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.common.exception.BusinessRuleException;
import com.talentpipe.common.exception.ResourceNotFoundException;
import com.talentpipe.job.dto.VacancyApplicationTarget;
import com.talentpipe.job.service.VacancyApplicationGate;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * End-to-end tests for the vacancy lifecycle (PB-018 → PB-022) through the
 * real HTTP API against a real PostgreSQL: the state machine, optimistic
 * locking, tenant isolation, the role rules, duplication, archive retention,
 * the application gate's row lock, and the database constraints behind them.
 *
 * <p>The cases follow §12 of the frontend's backend-contract document
 * ({@code docs/backend/job-vacancies-api.md} in talentpipe-frontend).</p>
 */
class JobVacancyFlowIntegrationTest extends AbstractJobIntegrationTest {

    @Autowired
    private VacancyApplicationGate applicationGate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    // ------------------------------------------------------------------- roles

    @Test
    void adminAndHrManager_canManageVacancies_interviewerAndAnonymousCannot() {
        Workspace acme = newWorkspace("Acme");
        String hrManager = newMember(acme, "HR_MANAGER");
        String interviewer = newMember(acme, "INTERVIEWER");

        assertThat(call(HttpMethod.GET, "/api/v1/jobs", acme.adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        // An HR manager can do everything an admin can, including create.
        Map<String, Object> created = create(hrManager, completeVacancy("Hired by HR"));
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + created.get("id"), hrManager, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(call(HttpMethod.GET, "/api/v1/jobs", interviewer, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(call(HttpMethod.POST, "/api/v1/jobs", interviewer, completeVacancy("Nope")).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map<String, Object>> anonymous = call(HttpMethod.GET, "/api/v1/jobs", null, null);
        assertThat(anonymous.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(anonymous.getBody()).containsKeys("timestamp", "status", "error", "message", "path");
    }

    // ------------------------------------------------------------------ create

    @Test
    void createDraft_storesItAndReturnsTheContractShape() {
        Workspace acme = newWorkspace("Acme");

        Map<String, Object> vacancy = create(acme.adminToken(), completeVacancy("Senior Backend Engineer"));

        assertThat(vacancy.get("id")).isNotNull();
        assertThat(vacancy)
                .containsEntry("status", "DRAFT")
                .containsEntry("version", 0)
                .containsEntry("title", "Senior Backend Engineer")
                .containsEntry("requiredSkills", List.of("Java", "Spring Boot"))
                .containsEntry("workingDays", List.of("MON", "TUE", "WED", "THU", "FRI"));
        assertThat(vacancy.get("createdAt")).isNotNull();
        assertThat(vacancy.get("updatedAt")).isNotNull();
        assertThat(vacancy)
                .containsEntry("publishedAt", null)
                .containsEntry("closedAt", null)
                .containsEntry("archivedAt", null)
                // Null, not zero, until the applications module exists.
                .containsEntry("applicantCount", null)
                // The tenant never appears in a response.
                .doesNotContainKey("tenantId");
    }

    @Test
    void createDraft_mayBeAlmostEmpty_butTheSameBodyCannotBePublished() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("");
        body.put("jobSummary", "");
        body.put("applicationDeadline", null);

        assertThat(create(acme.adminToken(), body)).containsEntry("status", "DRAFT");

        body.put("status", "PUBLISHED");
        ResponseEntity<Map<String, Object>> refused = call(HttpMethod.POST, "/api/v1/jobs", acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "This vacancy can't be published yet: "
                        + "add a job title, an application deadline and a job summary.");
    }

    @Test
    void createPublished_goesLiveImmediately_withPublishedAtSetInTheSameRequest() {
        Workspace acme = newWorkspace("Acme");

        Map<String, Object> vacancy = createPublished(acme.adminToken(), "Live from the start");

        assertThat(vacancy).containsEntry("status", "PUBLISHED");
        assertThat(vacancy.get("publishedAt")).isNotNull();
        // The detail page treats publishedAt within seconds of createdAt as "never a draft".
        Instant createdAt = Instant.parse((String) vacancy.get("createdAt"));
        Instant publishedAt = Instant.parse((String) vacancy.get("publishedAt"));
        assertThat(publishedAt).isBetween(createdAt.minusSeconds(5), createdAt.plusSeconds(5));
    }

    @Test
    void create_inAStatusOtherThanDraftOrPublished_isABadRequest() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Born closed");
        body.put("status", "CLOSED");

        assertThat(call(HttpMethod.POST, "/api/v1/jobs", acme.adminToken(), body).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_stripsHtmlBeforeStoring_soNoMarkupReachesThePublicBoard() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("<b>Engineer</b><script>alert(1)</script>");
        body.put("jobDescription", "Own the service.<img src=x onerror=alert(1)>");

        Map<String, Object> vacancy = create(acme.adminToken(), body);

        assertThat(vacancy)
                .containsEntry("title", "Engineer")
                .containsEntry("jobDescription", "Own the service.");
    }

    @Test
    void create_withAnAssigneeFromAnotherTenant_isRefused_butOwnActiveMemberIsAccepted() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        UUID globexAdmin = userIdWithRole(globex, "COMPANY_ADMIN");
        UUID acmeAdmin = userIdWithRole(acme, "COMPANY_ADMIN");

        Map<String, Object> body = completeVacancy("Needs a recruiter");
        body.put("assignedRecruiterId", globexAdmin.toString());
        ResponseEntity<Map<String, Object>> refused = call(HttpMethod.POST, "/api/v1/jobs", acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "The assigned recruiter must be an active member of your workspace.");

        body.put("assignedRecruiterId", acmeAdmin.toString());
        assertThat(create(acme.adminToken(), body)).containsEntry("assignedRecruiterId", acmeAdmin.toString());
    }

    @Test
    void create_withSalaryButNoCurrency_isRefusedAsABusinessRule() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Paid somehow");
        body.put("salaryMin", 450000);
        body.put("salaryMax", 650000);

        ResponseEntity<Map<String, Object>> refused = call(HttpMethod.POST, "/api/v1/jobs", acme.adminToken(), body);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody()).containsEntry("message", "Choose a currency for the salary range you entered.");
    }

    // ------------------------------------------------------------ state machine

    @Test
    void theFullLifecycle_draftToArchived_stampsEachTransitionAndKeepsTheHistory() {
        Workspace acme = newWorkspace("Acme");
        Object id = create(acme.adminToken(), completeVacancy("Lifecycle")).get("id");

        Map<String, Object> published = move(acme.adminToken(), id, "publish").getBody();
        assertThat(published)
                .containsEntry("status", "PUBLISHED")
                .containsEntry("version", 1);
        assertThat(published.get("publishedAt")).isNotNull();

        Map<String, Object> closed = move(acme.adminToken(), id, "close").getBody();
        assertThat(closed)
                .containsEntry("status", "CLOSED")
                .containsEntry("publishedAt", published.get("publishedAt"));
        assertThat(closed.get("closedAt")).isNotNull();

        Map<String, Object> archived = move(acme.adminToken(), id, "archive").getBody();
        assertThat(archived)
                .containsEntry("status", "ARCHIVED")
                // Retained for reporting: the advert and all three timestamps survive.
                .containsEntry("publishedAt", published.get("publishedAt"))
                .containsEntry("closedAt", closed.get("closedAt"))
                .containsEntry("title", "Lifecycle");
        assertThat(archived.get("archivedAt")).isNotNull();
        assertThat(Instant.parse((String) archived.get("updatedAt")))
                .isAfterOrEqualTo(Instant.parse((String) published.get("updatedAt")));
    }

    @Test
    void everyIllegalMove_answers422_andLeavesTheVacancyWhereItWas() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();

        Object draft = create(token, completeVacancy("Draft")).get("id");
        assertRefused(token, draft, "close", "Only a published vacancy can be closed. This vacancy is a draft.");
        assertRefused(token, draft, "archive", "Only a closed vacancy can be archived. This vacancy is a draft.");

        Object published = createPublished(token, "Published").get("id");
        assertRefused(token, published, "publish",
                "Only a draft vacancy can be published. This vacancy is already published.");
        assertRefused(token, published, "archive",
                "Only a closed vacancy can be archived. This vacancy is published.");

        Object closed = createPublished(token, "Closed").get("id");
        advance(token, closed, "close");
        assertRefused(token, closed, "publish", "Only a draft vacancy can be published. This vacancy is closed.");
        assertRefused(token, closed, "close",
                "Only a published vacancy can be closed. This vacancy is already closed.");

        Object archived = createPublished(token, "Archived").get("id");
        advance(token, archived, "close", "archive");
        assertRefused(token, archived, "publish", "Only a draft vacancy can be published. This vacancy is archived.");
        assertRefused(token, archived, "close", "Only a published vacancy can be closed. This vacancy is archived.");
        assertRefused(token, archived, "archive",
                "Only a closed vacancy can be archived. This vacancy is already archived.");
    }

    @Test
    void publish_anIncompleteDraft_isRefusedWithEverythingThatIsMissing() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Half written");
        body.put("applicationDeadline", null);
        body.put("requiredSkills", List.of());
        Object id = create(acme.adminToken(), body).get("id");

        ResponseEntity<Map<String, Object>> refused = move(acme.adminToken(), id, "publish");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "This vacancy can't be published yet: "
                        + "add an application deadline and at least one required skill.");
        assertThat(statusOf(acme.adminToken(), id)).isEqualTo("DRAFT");
    }

    @Test
    void publish_withADeadlineThatHasPassed_isRefused() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Too late");
        body.put("applicationDeadline", LocalDate.now().minusDays(3).toString());
        Object id = create(acme.adminToken(), body).get("id");

        ResponseEntity<Map<String, Object>> refused = move(acme.adminToken(), id, "publish");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat((String) refused.getBody().get("message"))
                .contains("an application deadline that is today or later");
    }

    // -------------------------------------------------------------------- edit

    @Test
    void edit_withTheCurrentVersion_savesAndBumpsTheVersion_andIsReflectedImmediately() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Original title");
        Object id = create(acme.adminToken(), body).get("id");

        body.put("title", "Corrected title");
        body.put("version", 0);
        ResponseEntity<Map<String, Object>> saved = call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody())
                .containsEntry("title", "Corrected title")
                .containsEntry("version", 1)
                .containsEntry("status", "DRAFT");

        // The very next read sees it — there is no cache to wait out.
        Map<String, Object> reloaded = call(HttpMethod.GET, "/api/v1/jobs/" + id, acme.adminToken(), null).getBody();
        assertThat(reloaded)
                .containsEntry("title", "Corrected title")
                .containsEntry("version", 1);
    }

    @Test
    void edit_withAStaleVersion_answers409_andTheFirstSaveWins() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Shared draft");
        Object id = create(acme.adminToken(), body).get("id");

        // Recruiter A saves against version 0…
        body.put("title", "Saved by A");
        body.put("version", 0);
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        // …then recruiter B, who opened the form before A saved, tries too.
        body.put("title", "Saved by B");
        ResponseEntity<Map<String, Object>> conflict =
                call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat((String) conflict.getBody().get("message")).startsWith("Someone else saved changes");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + id, acme.adminToken(), null).getBody())
                .containsEntry("title", "Saved by A");
    }

    @Test
    void edit_neverChangesStatus_evenWhenTheBodyAsksItTo() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Sneaky publish");
        Object id = create(acme.adminToken(), body).get("id");

        body.put("status", "PUBLISHED");
        body.put("version", 0);
        ResponseEntity<Map<String, Object>> saved = call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);

        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("status", "DRAFT");
        assertThat(saved.getBody().get("publishedAt")).isNull();
    }

    @Test
    void edit_ofAPublishedVacancy_isAllowed_butNotIfItWouldBecomeIncomplete() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Live role");
        body.put("status", "PUBLISHED");
        Map<String, Object> live = create(acme.adminToken(), body);
        Object id = live.get("id");

        body.put("jobSummary", "A sharper summary.");
        body.put("version", live.get("version"));
        ResponseEntity<Map<String, Object>> saved = call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);
        assertThat(saved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(saved.getBody()).containsEntry("status", "PUBLISHED");

        body.put("requiredSkills", List.of());
        body.put("version", saved.getBody().get("version"));
        ResponseEntity<Map<String, Object>> refused =
                call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(refused.getBody())
                .containsEntry("message", "A published vacancy has to stay complete: add at least one required skill.");
    }

    @Test
    void edit_ofAClosedOrArchivedVacancy_answers422() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> body = completeVacancy("Finished");
        body.put("status", "PUBLISHED");
        Object id = create(acme.adminToken(), body).get("id");
        advance(acme.adminToken(), id, "close");

        body.put("title", "Rewriting history");
        body.put("version", 2);
        ResponseEntity<Map<String, Object>> closed = call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(closed.getBody()).containsEntry("message", "This vacancy is closed and can no longer be edited.");

        advance(acme.adminToken(), id, "archive");
        body.put("version", 3);
        ResponseEntity<Map<String, Object>> archived =
                call(HttpMethod.PUT, "/api/v1/jobs/" + id, acme.adminToken(), body);
        assertThat(archived.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(archived.getBody())
                .containsEntry("message", "This vacancy is archived and can no longer be edited.");
    }

    // --------------------------------------------------------------- duplicate

    @Test
    void duplicate_createsAnEditableDraftPrefilledFromTheSource_andLeavesTheSourceAlone() {
        Workspace acme = newWorkspace("Acme");
        Map<String, Object> source = createPublished(acme.adminToken(), "Senior Backend Engineer");

        ResponseEntity<Map<String, Object>> response = move(acme.adminToken(), source.get("id"), "duplicate");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<String, Object> copy = response.getBody();
        assertThat(copy)
                .doesNotContainEntry("id", source.get("id"))
                .containsEntry("status", "DRAFT")
                .containsEntry("version", 0)
                .containsEntry("title", "Senior Backend Engineer (copy)")
                .containsEntry("requiredSkills", source.get("requiredSkills"))
                .containsEntry("jobDescription", source.get("jobDescription"))
                .containsEntry("benefits", source.get("benefits"));
        assertThat(copy.get("applicationDeadline")).isNull();
        assertThat(copy.get("publishedAt")).isNull();

        // Editable before publishing: it is an ordinary draft.
        Map<String, Object> edit = completeVacancy("Senior Backend Engineer — Platform");
        edit.put("version", 0);
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + copy.get("id"), acme.adminToken(), edit).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        advance(acme.adminToken(), copy.get("id"), "publish");

        // The source is exactly as it was.
        Map<String, Object> sourceNow =
                call(HttpMethod.GET, "/api/v1/jobs/" + source.get("id"), acme.adminToken(), null).getBody();
        assertThat(sourceNow)
                .containsEntry("title", "Senior Backend Engineer")
                .containsEntry("status", "PUBLISHED")
                .containsEntry("version", source.get("version"));
    }

    @Test
    void duplicate_worksFromAnArchivedVacancy_itIsStillAGoodTemplate() {
        Workspace acme = newWorkspace("Acme");
        Object id = createPublished(acme.adminToken(), "Seasonal role").get("id");
        advance(acme.adminToken(), id, "close", "archive");

        ResponseEntity<Map<String, Object>> response = move(acme.adminToken(), id, "duplicate");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsEntry("status", "DRAFT");
        assertThat(statusOf(acme.adminToken(), id)).isEqualTo("ARCHIVED");
    }

    // ------------------------------------------------- lists, archive, reports

    @Test
    @SuppressWarnings("unchecked")
    void list_returnsEveryVacancyNewestFirst_archivedIncluded_andFiltersByStatus() {
        Workspace acme = newWorkspace("Acme");
        String token = acme.adminToken();
        Object draft = create(token, completeVacancy("First: a draft")).get("id");
        Object archived = createPublished(token, "Second: archived").get("id");
        advance(token, archived, "close", "archive");
        Object published = createPublished(token, "Third: published").get("id");

        Map<String, Object> all = call(HttpMethod.GET, "/api/v1/jobs?page=0&size=100", token, null).getBody();
        assertThat(ids((List<Map<String, Object>>) all.get("content")))
                .containsExactly(published, archived, draft);
        assertThat(all)
                .containsEntry("totalElements", 3)
                .containsEntry("size", 100);

        // "Hidden from active lists…"
        Map<String, Object> active = call(HttpMethod.GET, "/api/v1/jobs?status=ACTIVE", token, null).getBody();
        assertThat(ids((List<Map<String, Object>>) active.get("content"))).containsExactly(published, draft);

        // "…but retained": still listed on request, still fetchable by id.
        Map<String, Object> onlyArchived = call(HttpMethod.GET, "/api/v1/jobs?status=ARCHIVED", token, null).getBody();
        assertThat(ids((List<Map<String, Object>>) onlyArchived.get("content"))).containsExactly(archived);
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + archived, token, null).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(call(HttpMethod.GET, "/api/v1/jobs?status=DELETED", token, null).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_pagesCorrectly() {
        Workspace acme = newWorkspace("Acme");
        for (int i = 1; i <= 5; i++) {
            create(acme.adminToken(), completeVacancy("Role " + i));
        }

        Map<String, Object> second = call(HttpMethod.GET, "/api/v1/jobs?page=1&size=2", acme.adminToken(), null).getBody();

        assertThat((List<Object>) second.get("content")).hasSize(2);
        assertThat(second)
                .containsEntry("page", 1)
                .containsEntry("totalElements", 5)
                .containsEntry("totalPages", 3);
        assertThat(((List<Map<String, Object>>) second.get("content")).get(0)).containsEntry("title", "Role 3");
    }

    @Test
    void counts_reportEveryStatus_includingArchived_forThisTenantOnly() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        String token = acme.adminToken();
        create(token, completeVacancy("Draft 1"));
        create(token, completeVacancy("Draft 2"));
        createPublished(token, "Published");
        Object archived = createPublished(token, "Archived").get("id");
        advance(token, archived, "close", "archive");
        createPublished(globex.adminToken(), "Someone else's");

        Map<String, Object> counts = call(HttpMethod.GET, "/api/v1/jobs/counts", token, null).getBody();

        assertThat(counts).containsOnly(
                Map.entry("DRAFT", 2), Map.entry("PUBLISHED", 1), Map.entry("CLOSED", 0), Map.entry("ARCHIVED", 1));
    }

    @Test
    void archiving_neverDeletesTheRow() {
        Workspace acme = newWorkspace("Acme");
        Object id = createPublished(acme.adminToken(), "Kept forever").get("id");
        advance(acme.adminToken(), id, "close", "archive");

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, published_at, closed_at, archived_at, title FROM job_vacancies WHERE id = ?::uuid", id);

        assertThat(row)
                .containsEntry("status", "ARCHIVED")
                .containsEntry("title", "Kept forever");
        assertThat(row.get("published_at")).isNotNull();
        assertThat(row.get("closed_at")).isNotNull();
        assertThat(row.get("archived_at")).isNotNull();
    }

    // --------------------------------------------------------- tenant isolation

    @Test
    void anotherTenantsVacancy_answers404OnEveryEndpoint_neverA403() {
        Workspace acme = newWorkspace("Acme");
        Workspace globex = newWorkspace("Globex");
        Map<String, Object> body = completeVacancy("Acme's secret role");
        Object id = create(acme.adminToken(), body).get("id");
        String intruder = globex.adminToken();
        body.put("version", 0);

        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + id, intruder, null).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(call(HttpMethod.PUT, "/api/v1/jobs/" + id, intruder, body).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        for (String move : List.of("publish", "close", "archive", "duplicate")) {
            assertThat(move(intruder, id, move).getStatusCode()).as(move).isEqualTo(HttpStatus.NOT_FOUND);
        }
        // Indistinguishable from an id that was never issued.
        assertThat(call(HttpMethod.GET, "/api/v1/jobs/" + UUID.randomUUID(), intruder, null).getBody())
                .containsEntry("message",
                        call(HttpMethod.GET, "/api/v1/jobs/" + id, intruder, null).getBody().get("message"));

        // And nothing was changed or copied.
        assertThat(statusOf(acme.adminToken(), id)).isEqualTo("DRAFT");
        assertThat(call(HttpMethod.GET, "/api/v1/jobs", intruder, null).getBody()).containsEntry("totalElements", 0);
    }

    // -------------------------------------------- close stops new applications

    @Test
    void applicationGate_letsApplicationsInWhilePublished_andRefusesThemOnceClosed() {
        Workspace acme = newWorkspace("Acme");
        UUID id = UUID.fromString((String) createPublished(acme.adminToken(), "Apply here").get("id"));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        VacancyApplicationTarget target = tx.execute(status -> applicationGate.requireOpen(id));
        assertThat(target.vacancyId()).isEqualTo(id);
        assertThat(target.tenantId()).isEqualTo(acme.tenantId());
        assertThat(target.title()).isEqualTo("Apply here");

        advance(acme.adminToken(), id, "close");

        assertThatThrownBy(() -> tx.execute(status -> applicationGate.requireOpen(id)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("This vacancy is closed and no longer accepts applications.");
    }

    @Test
    void applicationGate_treatsADraftAsNonExistent_andRefusesAfterTheDeadline() {
        Workspace acme = newWorkspace("Acme");
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        UUID draft = UUID.fromString((String) create(acme.adminToken(), completeVacancy("Not public")).get("id"));
        UUID live = UUID.fromString((String) createPublished(acme.adminToken(), "Deadline passed").get("id"));

        assertThatThrownBy(() -> tx.execute(status -> applicationGate.requireOpen(draft)))
                .isInstanceOf(ResourceNotFoundException.class);

        // Time passing, simulated: the vacancy is still PUBLISHED but its deadline is now behind us.
        jdbc.update("UPDATE job_vacancies SET application_deadline = current_date - 5 WHERE id = ?", live);

        assertThatThrownBy(() -> tx.execute(status -> applicationGate.requireOpen(live)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageStartingWith("Applications for this vacancy closed on ");
    }

    @Test
    void applicationGate_holdsARowLock_soACloseWaitsForAnInFlightApplication() throws Exception {
        Workspace acme = newWorkspace("Acme");
        UUID id = UUID.fromString((String) createPublished(acme.adminToken(), "Racing to close").get("id"));
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        AtomicReference<CompletableFuture<ResponseEntity<Map<String, Object>>>> closing = new AtomicReference<>();

        // An application is being submitted: the gate has passed and its
        // transaction (which would insert the application) is still open.
        tx.executeWithoutResult(status -> {
            applicationGate.requireOpen(id);

            closing.set(CompletableFuture.supplyAsync(() -> move(acme.adminToken(), id, "close")));

            // The recruiter's close cannot complete while that transaction lives.
            assertThatThrownBy(() -> closing.get().get(800, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
        });

        // The application committed; now the close goes through.
        ResponseEntity<Map<String, Object>> closed = closing.get().get(15, TimeUnit.SECONDS);
        assertThat(closed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(closed.getBody()).containsEntry("status", "CLOSED");
    }

    // ------------------------------------------------------ database backstops

    @Test
    void theDatabaseItself_refusesAStateTheStateMachineCouldNeverProduce() {
        Workspace acme = newWorkspace("Acme");
        Object draft = create(acme.adminToken(), completeVacancy("Constraint check")).get("id");

        // ARCHIVED with no published/closed/archived timestamps: impossible via the API.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE job_vacancies SET status = 'ARCHIVED' WHERE id = ?::uuid", draft))
                .isInstanceOf(DataIntegrityViolationException.class);
        // An unknown status.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE job_vacancies SET status = 'DELETED' WHERE id = ?::uuid", draft))
                .isInstanceOf(DataIntegrityViolationException.class);
        // A salary range the wrong way round.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE job_vacancies SET salary_min = 500, salary_max = 100 WHERE id = ?::uuid", draft))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void malformedVacancyId_isABadRequest_notAServerError() {
        Workspace acme = newWorkspace("Acme");

        ResponseEntity<Map<String, Object>> response =
                call(HttpMethod.GET, "/api/v1/jobs/not-a-uuid", acme.adminToken(), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ----------------------------------------------------------------- helpers

    private void assertRefused(String token, Object vacancyId, String move, String expectedMessage) {
        String before = statusOf(token, vacancyId);

        ResponseEntity<Map<String, Object>> response = move(token, vacancyId, move);

        assertThat(response.getStatusCode()).as("%s from %s", move, before).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).containsEntry("message", expectedMessage);
        assertThat(statusOf(token, vacancyId)).as("status after refused %s", move).isEqualTo(before);
    }

    private String statusOf(String token, Object vacancyId) {
        return (String) call(HttpMethod.GET, "/api/v1/jobs/" + vacancyId, token, null).getBody().get("status");
    }

    private static List<Object> ids(List<Map<String, Object>> vacancies) {
        return vacancies.stream().map(vacancy -> vacancy.get("id")).toList();
    }
}
