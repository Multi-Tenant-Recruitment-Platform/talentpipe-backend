package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.talentpipe.application.entity.ApplicationScore;
import com.talentpipe.application.entity.CriterionScore;
import com.talentpipe.application.entity.ScreeningResult;
import com.talentpipe.application.entity.ScreeningStatus;
import com.talentpipe.application.repository.ApplicationScoreRepository;
import com.talentpipe.common.tenant.TenantContext;
import com.talentpipe.job.entity.JobScreeningCriteria;
import com.talentpipe.job.repository.JobScreeningCriteriaRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V26 as PostgreSQL enforces it — constraints, what deleting does, Row-Level
 * Security, and that it applies to a database already holding CVs and
 * applications — and its two new tables as the ORM maps and scopes them.
 *
 * <p>The schema rules are tested with plain SQL on purpose: they hold even for a
 * write that never went through the service layer.</p>
 */
class ScreeningSchemaIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ApplicationScoreRepository scores;

    @Autowired
    private JobScreeningCriteriaRepository criteria;

    private UUID tenant;
    private UUID vacancy;
    private UUID candidate;
    private UUID application;

    @BeforeEach
    void anApplicationInACompany() {
        tenant = newTenant(jdbc);
        vacancy = newVacancy(jdbc, tenant);
        candidate = newCandidate(jdbc);
        application = newApplication(jdbc, vacancy, candidate, tenant);
    }

    // ---------------------------------------------------------------- CV text

    @Test
    void aCvStartsPending_withNoText() {
        UUID cv = newCv(candidate);

        assertThat(jdbc.queryForMap(
                "SELECT extraction_status, extracted_text, text_extracted_at FROM candidate_cvs WHERE id = ?", cv))
                .containsEntry("extraction_status", "PENDING")
                .containsEntry("extracted_text", null)
                .containsEntry("text_extracted_at", null);
    }

    @Test
    void anUnknownExtractionStatus_isRefused() {
        UUID cv = newCv(candidate);

        assertThatThrownBy(() -> jdbc.update("UPDATE candidate_cvs SET extraction_status = 'DONE' WHERE id = ?", cv))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_candidate_cvs_extraction_status");
    }

    @Test
    void anExtractedCv_hasItsTextAndWhenItWasRead() {
        UUID cv = newCv(candidate);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE candidate_cvs SET extraction_status = 'EXTRACTED', text_extracted_at = now() WHERE id = ?", cv))
                .hasMessageContaining("chk_candidate_cvs_extracted");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE candidate_cvs SET extraction_status = 'EXTRACTED', extracted_text = 'text' WHERE id = ?", cv))
                .hasMessageContaining("chk_candidate_cvs_extracted");
        assertThat(jdbc.update("UPDATE candidate_cvs SET extraction_status = 'EXTRACTED', extracted_text = 'text', "
                + "text_extracted_at = now() WHERE id = ?", cv)).isOne();
    }

    // ------------------------------------------------------- an application's CV

    @Test
    void anApplicationCannotBeMadeWithSomeoneElsesCv() {
        UUID theirs = newCv(newCandidate(jdbc));

        assertThatThrownBy(() -> jdbc.update("UPDATE applications SET cv_id = ? WHERE id = ?", theirs, application))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_applications_cv");
    }

    @Test
    void deletingACv_clearsTheLink_andKeepsTheApplicationAndItsCandidate() {
        UUID cv = newCv(candidate);
        jdbc.update("UPDATE applications SET cv_id = ? WHERE id = ?", cv, application);

        jdbc.update("DELETE FROM candidate_cvs WHERE id = ?", cv);

        assertThat(jdbc.queryForMap("SELECT cv_id, candidate_id FROM applications WHERE id = ?", application))
                .containsEntry("cv_id", null)
                .containsEntry("candidate_id", candidate);
    }

    @Test
    void deletingACandidate_deletesTheirCvsApplicationsAndScores() {
        jdbc.update("UPDATE applications SET cv_id = ? WHERE id = ?", newCv(candidate), application);
        insertFailedScore(tenant, application);

        jdbc.update("DELETE FROM candidates WHERE id = ?", candidate);

        assertThat(count("candidate_cvs")).isZero();
        assertThat(count("applications")).isZero();
        assertThat(count("application_scores")).isZero();
    }

    // ------------------------------------------------------------------ scores

    @Test
    void anApplicationHasOneScoreAtMost() {
        insertFailedScore(tenant, application);

        assertThatThrownBy(() -> insertFailedScore(tenant, application))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_application_scores_application");
    }

    @Test
    void aScoreCannotBelongToAnotherCompanyThanItsApplication() {
        UUID otherTenant = newTenant(jdbc);

        assertThatThrownBy(() -> insertFailedScore(otherTenant, application))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_application_scores_application");
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void aScoreOffTheScale_isRefused(int overallScore) {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO application_scores (application_id, tenant_id, status, "
                + "overall_score) VALUES (?, ?, 'SCORING_FAILED', ?)", application, tenant, overallScore))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("overall_score");
    }

    @Test
    void anUnknownScoreStatus_isRefused() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO application_scores (application_id, tenant_id, status) "
                + "VALUES (?, ?, 'DONE')", application, tenant))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("application_scores_status_check");
    }

    @ParameterizedTest
    @ValueSource(strings = {"overall_score", "summary", "model_version", "scored_at"})
    void aScoredApplication_hasAScore_andSaysWhereItCameFrom(String column) {
        jdbc.update("INSERT INTO application_scores (application_id, tenant_id, status, overall_score, summary, "
                + "model_version, scored_at) VALUES (?, ?, 'SCORED', 80, 'Fine', 'gemini-x', now())",
                application, tenant);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE application_scores SET " + column + " = NULL WHERE application_id = ?", application))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_application_scores_state");
    }

    @ParameterizedTest
    @ValueSource(strings = {"strengths", "gaps", "criterion_scores"})
    void anythingButAList_isRefusedWhereAListBelongs(String column) {
        insertFailedScore(tenant, application);

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE application_scores SET " + column + " = '{}'::jsonb WHERE application_id = ?", application))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_application_scores_lists");
    }

    @Test
    void deletingAnApplication_deletesItsScore() {
        insertFailedScore(tenant, application);

        jdbc.update("DELETE FROM applications WHERE id = ?", application);

        assertThat(count("application_scores")).isZero();
    }

    // ---------------------------------------------------------------- criteria

    @Test
    void aVacancyCannotHaveTheSameCriterionTwice_butAnotherVacancyCanHaveIt() {
        insertCriterion(tenant, vacancy, "Java", "0.5");

        assertThatThrownBy(() -> insertCriterion(tenant, vacancy, "Java", "0.3"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_criteria_job_name");
        insertCriterion(tenant, newVacancy(jdbc, tenant), "Java", "0.3");
        assertThat(count("job_screening_criteria")).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.1", "1.001"})
    void aWeightIsAboveZeroAndAtMostOne(String weight) {
        assertThatThrownBy(() -> insertCriterion(tenant, vacancy, "Java", weight))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("job_screening_criteria_weight_check");
    }

    @Test
    void aCriterionCannotBelongToAnotherCompanyThanItsVacancy() {
        UUID otherTenant = newTenant(jdbc);

        assertThatThrownBy(() -> insertCriterion(otherTenant, vacancy, "Java", "0.5"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_job_screening_criteria_job");
    }

    @Test
    void deletingAVacancy_deletesItsCriteria() {
        insertCriterion(tenant, vacancy, "Java", "0.5");

        jdbc.update("DELETE FROM job_vacancies WHERE id = ?", vacancy);

        assertThat(count("job_screening_criteria")).isZero();
    }

    // ------------------------------------------------------------ row security

    @ParameterizedTest
    @ValueSource(strings = {"application_scores", "job_screening_criteria"})
    void rowLevelSecurity_isOn_withATenantIsolationPolicy(String table) {
        assertThat(jdbc.queryForObject("SELECT relrowsecurity FROM pg_class WHERE oid = ?::regclass",
                Boolean.class, table)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_policies WHERE tablename = ? AND policyname = ? "
                + "AND cmd = 'ALL'", Long.class, table, "tenant_isolation_" + table)).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"application_scores", "job_screening_criteria"})
    void rowLevelSecurity_showsARoleOnlyTheRowsOfTheTenantInContext(String table) {
        insertFailedScore(tenant, application);
        insertCriterion(tenant, vacancy, "Java", "0.5");
        UUID otherTenant = newTenant(jdbc);
        UUID otherVacancy = newVacancy(jdbc, otherTenant);
        insertFailedScore(otherTenant, newApplication(jdbc, otherVacancy, newCandidate(jdbc), otherTenant));
        insertFailedScore(otherTenant, newApplication(jdbc, otherVacancy, newCandidate(jdbc), otherTenant));
        insertCriterion(otherTenant, otherVacancy, "Java", "0.5");
        insertCriterion(otherTenant, otherVacancy, "Kafka", "0.5");

        // The table owner bypasses RLS, so the check runs as a plain role.
        String probe = "rls_probe_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + probe + " NOLOGIN");
        try {
            jdbc.execute("GRANT SELECT ON " + table + " TO " + probe);
            Long visible = new TransactionTemplate(transactionManager).execute(status -> {
                jdbc.execute("SET LOCAL ROLE " + probe);
                jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class,
                        tenant.toString());
                return count(table);
            });
            assertThat(visible).isOne();
        } finally {
            jdbc.execute("DROP OWNED BY " + probe);
            jdbc.execute("DROP ROLE " + probe);
        }
    }

    // --------------------------------------------------------------------- ORM

    @Test
    void aScore_isReadBackAsItWasStored_andScreeningAgainUpdatesTheSameRow() {
        ScreeningResult result = new ScreeningResult(82, "A strong backend engineer.",
                List.of("Java", "\"Quoted\" & unicode: ශ්‍රී"), List.of(),
                List.of(new CriterionScore("Java", 90, "Seven years"), new CriterionScore("Kafka", 40, "None")),
                "gemini-x", "v3", false);
        Instant scoredAt = Instant.parse("2026-11-26T08:00:00Z");
        UUID id = inTransaction(() ->
                scores.save(ApplicationScore.createScored(application, tenant, result, scoredAt)).getId());

        ApplicationScore stored = inTransaction(() -> scores.findByApplicationId(application).orElseThrow());
        assertThat(stored.getId()).isEqualTo(id);
        assertThat(stored.getStatus()).isEqualTo(ScreeningStatus.SCORED);
        assertThat(stored.getOverallScore()).isEqualTo(82);
        assertThat(stored.getStrengths()).containsExactlyElementsOf(result.strengths());
        assertThat(stored.getGaps()).isEmpty();
        assertThat(stored.getCriterionScores()).containsExactlyElementsOf(result.criterionScores());
        assertThat(stored.getScoredAt()).isEqualTo(scoredAt);

        inTransaction(() -> {
            scores.findByApplicationId(application).orElseThrow().recordFailure("Service unavailable");
            return null;
        });

        assertThat(jdbc.queryForMap("SELECT id, status, overall_score, strengths::text AS strengths, failure_reason "
                + "FROM application_scores"))
                .containsEntry("id", id)
                .containsEntry("status", "SCORING_FAILED")
                .containsEntry("overall_score", null)
                .containsEntry("strengths", "[]")
                .containsEntry("failure_reason", "Service unavailable");
    }

    @Test
    void criteria_areReadBackByName() {
        inTransaction(() -> criteria.saveAll(List.of(
                new JobScreeningCriteria(tenant, vacancy, "Kafka", new BigDecimal("0.375")),
                new JobScreeningCriteria(tenant, vacancy, "Java", new BigDecimal("0.625")))));

        List<JobScreeningCriteria> stored = inTransaction(() -> criteria.findAllByJobIdOrderByNameAsc(vacancy));

        assertThat(stored).extracting(JobScreeningCriteria::getName).containsExactly("Java", "Kafka");
        assertThat(stored).extracting(JobScreeningCriteria::getWeight)
                .containsExactly(new BigDecimal("0.625"), new BigDecimal("0.375"));
        assertThat(stored).allSatisfy(criterion -> {
            assertThat(criterion.getTenantId()).isEqualTo(tenant);
            assertThat(criterion.getJobId()).isEqualTo(vacancy);
        });
    }

    /** No service in between: the ORM filter alone keeps one company's rows from another. */
    @Test
    void withACompanyInContext_anotherCompanysScoresAndCriteriaDoNotExist() {
        UUID otherTenant = newTenant(jdbc);
        UUID otherVacancy = newVacancy(jdbc, otherTenant);
        UUID otherApplication = newApplication(jdbc, otherVacancy, newCandidate(jdbc), otherTenant);
        UUID ourScore = insertFailedScore(tenant, application);
        UUID theirScore = insertFailedScore(otherTenant, otherApplication);
        UUID ourCriterion = insertCriterion(tenant, vacancy, "Java", "0.5");
        UUID theirCriterion = insertCriterion(otherTenant, otherVacancy, "Java", "0.5");

        TenantContext.set(tenant);
        try {
            inTransaction(() -> {
                assertThat(scores.findAll()).extracting(ApplicationScore::getId).containsExactly(ourScore);
                assertThat(scores.findById(theirScore)).isEmpty();
                assertThat(scores.findByApplicationId(otherApplication)).isEmpty();
                assertThat(criteria.findAll()).extracting(JobScreeningCriteria::getId).containsExactly(ourCriterion);
                assertThat(criteria.findById(theirCriterion)).isEmpty();
                assertThat(criteria.findAllByJobIdOrderByNameAsc(otherVacancy)).isEmpty();
                return null;
            });
        } finally {
            TenantContext.clear();
        }

        // Unscoped again once the company is gone from the context.
        assertThat(inTransaction(() -> scores.findAll())).hasSize(2);
        assertThat(inTransaction(() -> criteria.findAll())).hasSize(2);
    }

    // ------------------------------------------------------ populated database

    @Test
    void v26_appliesToADatabaseThatAlreadyHoldsCvsAndApplications() {
        String database = "populated_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database,
                    POSTGRES.getUsername(), POSTGRES.getPassword());
            migrate(dataSource, "25");

            // Data as it stood before V26: an archived CV, an active one, and an application.
            JdbcTemplate db = new JdbcTemplate(dataSource);
            UUID company = newTenant(db);
            UUID person = newCandidate(db);
            UUID oldCv = insertCv(db, person, 1, false);
            UUID activeCv = insertCv(db, person, 2, true);
            UUID earlier = newApplication(db, newVacancy(db, company), person, company);

            migrate(dataSource, "latest");

            assertThat(db.queryForList("SELECT extraction_status FROM candidate_cvs WHERE id IN (?, ?)",
                    String.class, oldCv, activeCv)).containsExactly("PENDING", "PENDING");
            // Which CV it was made with was never recorded, so it is not guessed.
            Map<String, Object> row = db.queryForMap("SELECT cv_id, status FROM applications WHERE id = ?", earlier);
            assertThat(row).containsEntry("cv_id", null).containsEntry("status", "APPLIED");
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void migrate(DriverManagerDataSource dataSource, String target) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(target)
                .load()
                .migrate();
    }

    private <T> T inTransaction(Supplier<T> work) {
        return new TransactionTemplate(transactionManager).execute(status -> work.get());
    }

    private Long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private UUID newCv(UUID candidateId) {
        return insertCv(jdbc, candidateId, 1, true);
    }

    private UUID insertFailedScore(UUID tenantId, UUID applicationId) {
        return jdbc.queryForObject("INSERT INTO application_scores (application_id, tenant_id, status, failure_reason) "
                + "VALUES (?, ?, 'SCORING_FAILED', 'x') RETURNING id", UUID.class, applicationId, tenantId);
    }

    private UUID insertCriterion(UUID tenantId, UUID jobId, String name, String weight) {
        return jdbc.queryForObject("INSERT INTO job_screening_criteria (tenant_id, job_id, name, weight) "
                + "VALUES (?, ?, ?, ?) RETURNING id", UUID.class, tenantId, jobId, name, new BigDecimal(weight));
    }

    private static UUID newTenant(JdbcTemplate db) {
        String subdomain = "t-" + UUID.randomUUID().toString().substring(0, 8);
        return db.queryForObject("INSERT INTO tenants (name, subdomain) VALUES (?, ?) RETURNING id",
                UUID.class, "Company " + subdomain, subdomain);
    }

    private static UUID newVacancy(JdbcTemplate db, UUID tenantId) {
        return db.queryForObject("INSERT INTO job_vacancies (tenant_id) VALUES (?) RETURNING id",
                UUID.class, tenantId);
    }

    private static UUID newCandidate(JdbcTemplate db) {
        return db.queryForObject("INSERT INTO candidates (email, password_hash, full_name, identity_card_number, "
                + "address, contact_number, status) VALUES (?, 'x', 'Kasun Perera', '200012345678', 'Colombo', "
                + "'+94 77 123 4567', 'ACTIVE') RETURNING id", UUID.class, UUID.randomUUID() + "@example.com");
    }

    private static UUID newApplication(JdbcTemplate db, UUID jobId, UUID candidateId, UUID tenantId) {
        return db.queryForObject("INSERT INTO applications (job_id, candidate_id, tenant_id) VALUES (?, ?, ?) "
                + "RETURNING id", UUID.class, jobId, candidateId, tenantId);
    }

    private static UUID insertCv(JdbcTemplate db, UUID candidateId, int version, boolean active) {
        return db.queryForObject("INSERT INTO candidate_cvs (candidate_id, file_name, storage_key, mime_type, "
                + "file_size_bytes, file_hash_sha256, version, is_active) "
                + "VALUES (?, 'cv.pdf', 'key', 'application/pdf', 10, 'hash', ?, ?) RETURNING id",
                UUID.class, candidateId, version, active);
    }
}
