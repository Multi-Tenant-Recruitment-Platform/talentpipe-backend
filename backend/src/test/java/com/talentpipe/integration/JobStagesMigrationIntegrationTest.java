package com.talentpipe.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The schema of V22 to V24 as PostgreSQL enforces it, independent of the
 * application code: the constraints on {@code job_stages}, its Row-Level
 * Security, how it and the vacancy columns behave when a vacancy or template
 * is deleted, and that the migrations apply to a database already holding
 * templates and vacancies — V24 filling in the pipeline summary of the
 * vacancies created before it.
 *
 * <p>Rows are written with plain SQL on purpose: these are the rules that
 * hold even for a write that never went through the service layer.</p>
 */
class JobStagesMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID tenant;
    private UUID vacancy;

    @BeforeEach
    void aCompanyWithAVacancy() {
        tenant = newTenant();
        vacancy = newVacancy(tenant);
    }

    // ----------------------------------------------------------- constraints

    @Test
    void aVacancyCannotHaveTwoStagesAtTheSamePosition() {
        insertStage(tenant, vacancy, 0, "POST_APPLICATION", "Applied", null);

        assertThatThrownBy(() -> insertStage(tenant, vacancy, 0, "SCREENING", "Screening", null))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("uq_job_stages_job_position");
    }

    @Test
    void anInterviewRoundWithoutABenchmark_isRefused() {
        assertThatThrownBy(() -> insertStage(tenant, vacancy, 1, "INTERVIEW", "Technical interview", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_stages_benchmark");
    }

    @Test
    void aBenchmarkOnAnythingButAnInterviewRound_isRefused() {
        assertThatThrownBy(() -> insertStage(tenant, vacancy, 1, "SCREENING", "Screening", 60))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_stages_benchmark");
    }

    @Test
    void aStageCannotBelongToAVacancyOfAnotherCompany() {
        UUID otherTenant = newTenant();

        assertThatThrownBy(() -> insertStage(otherTenant, vacancy, 0, "POST_APPLICATION", "Applied", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_job_stages_job");
    }

    @Test
    void aVacancysThresholds_stayOnTheScale_andInOrder() {
        assertThatThrownBy(() -> jdbc.update("UPDATE job_vacancies SET auto_threshold = 101 WHERE id = ?", vacancy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_vacancies_auto_threshold");
        assertThatThrownBy(() -> jdbc.update("UPDATE job_vacancies SET manual_threshold = -1 WHERE id = ?", vacancy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_vacancies_manual_threshold");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE job_vacancies SET auto_threshold = 70, manual_threshold = 70 WHERE id = ?", vacancy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_vacancies_thresholds");
    }

    @Test
    void aVacancysStageCounts_cannotBeNegative() {
        assertThatThrownBy(() -> jdbc.update("UPDATE job_vacancies SET stage_count = -1 WHERE id = ?", vacancy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_vacancies_stage_count");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE job_vacancies SET interview_round_count = -1 WHERE id = ?", vacancy))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("chk_job_vacancies_interview_round_count");
    }

    // --------------------------------------------------------------- deletes

    @Test
    void deletingAVacancy_deletesItsStages() {
        insertStage(tenant, vacancy, 0, "POST_APPLICATION", "Applied", null);
        insertStage(tenant, vacancy, 1, "INTERVIEW", "Technical interview", 75);

        jdbc.update("DELETE FROM job_vacancies WHERE id = ?", vacancy);

        assertThat(stagesOf(vacancy)).isZero();
    }

    @Test
    void deletingATemplate_keepsTheVacancyCreatedFromIt_andClearsOnlyTheLink() {
        UUID template = jdbc.queryForObject(
                "INSERT INTO pipeline_templates (tenant_id, name) VALUES (?, 'Engineering') RETURNING id",
                UUID.class, tenant);
        jdbc.update("UPDATE job_vacancies SET pipeline_template_id = ?, auto_threshold = 80, manual_threshold = 70 "
                + "WHERE id = ?", template, vacancy);
        insertStage(tenant, vacancy, 0, "POST_APPLICATION", "Applied", null);

        jdbc.update("DELETE FROM pipeline_templates WHERE id = ?", template);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT pipeline_template_id, auto_threshold, manual_threshold FROM job_vacancies WHERE id = ?",
                vacancy);
        assertThat(row)
                .containsEntry("pipeline_template_id", null)
                .containsEntry("auto_threshold", 80)
                .containsEntry("manual_threshold", 70);
        assertThat(stagesOf(vacancy)).isOne();
    }

    // ---------------------------------------------------------- row security

    @Test
    void rowLevelSecurity_isOnForJobStages_withATenantIsolationPolicy() {
        assertThat(jdbc.queryForObject(
                "SELECT relrowsecurity FROM pg_class WHERE oid = 'job_stages'::regclass", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM pg_policies WHERE tablename = 'job_stages' "
                        + "AND policyname = 'tenant_isolation_job_stages' AND cmd = 'ALL'", Long.class)).isOne();
    }

    @Test
    void rowLevelSecurity_showsARoleOnlyTheStagesOfTheTenantInContext() {
        UUID otherTenant = newTenant();
        UUID otherVacancy = newVacancy(otherTenant);
        insertStage(tenant, vacancy, 0, "POST_APPLICATION", "Applied", null);
        insertStage(otherTenant, otherVacancy, 0, "POST_APPLICATION", "Applied", null);
        insertStage(otherTenant, otherVacancy, 1, "ONBOARD", "Onboard", null);

        // The table owner bypasses RLS, so the check runs as a plain role.
        String probe = "rls_probe_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE ROLE " + probe + " NOLOGIN");
        try {
            jdbc.execute("GRANT SELECT ON job_stages TO " + probe);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            Long visible = transaction.execute(status -> {
                jdbc.execute("SET LOCAL ROLE " + probe);
                jdbc.queryForObject("SELECT set_config('app.current_tenant', ?, true)", String.class,
                        tenant.toString());
                return jdbc.queryForObject("SELECT count(*) FROM job_stages", Long.class);
            });
            assertThat(visible).isOne();
        } finally {
            jdbc.execute("DROP OWNED BY " + probe);
            jdbc.execute("DROP ROLE " + probe);
        }
    }

    // ------------------------------------------------------- populated database

    @Test
    void v22AndV23_applyToADatabaseThatAlreadyHoldsTemplatesAndVacancies() {
        String database = "populated_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database,
                    POSTGRES.getUsername(), POSTGRES.getPassword());
            migrate(dataSource, "21");

            // Data as it stood before V22: a company with a template and a vacancy.
            JdbcTemplate before = new JdbcTemplate(dataSource);
            UUID company = before.queryForObject(
                    "INSERT INTO tenants (name, subdomain) VALUES ('Acme', 'acme') RETURNING id", UUID.class);
            UUID template = before.queryForObject(
                    "INSERT INTO pipeline_templates (tenant_id, name) VALUES (?, 'Engineering') RETURNING id",
                    UUID.class, company);
            UUID oldVacancy = before.queryForObject(
                    "INSERT INTO job_vacancies (tenant_id) VALUES (?) RETURNING id", UUID.class, company);

            migrate(dataSource, "latest");

            JdbcTemplate after = new JdbcTemplate(dataSource);
            assertThat(after.queryForObject(
                    "SELECT blind_mode_enabled FROM pipeline_templates WHERE id = ?", Boolean.class, template))
                    .isFalse();
            assertThat(after.queryForMap("SELECT pipeline_template_id, auto_threshold, manual_threshold, "
                    + "blind_mode_enabled FROM job_vacancies WHERE id = ?", oldVacancy))
                    .containsEntry("pipeline_template_id", null)
                    .containsEntry("auto_threshold", null)
                    .containsEntry("manual_threshold", null)
                    .containsEntry("blind_mode_enabled", false);
            assertThat(after.queryForObject("SELECT count(*) FROM job_stages", Long.class)).isZero();
        } finally {
            jdbc.execute("DROP DATABASE IF EXISTS " + database + " WITH (FORCE)");
        }
    }

    @Test
    void v24_fillsInThePipelineSummary_ofVacanciesCreatedBeforeIt() {
        String database = "populated_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(
                    "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database,
                    POSTGRES.getUsername(), POSTGRES.getPassword());
            migrate(dataSource, "23");

            // Data as it stood before V24: one vacancy on a template with copied stages, one older than templates.
            JdbcTemplate before = new JdbcTemplate(dataSource);
            UUID company = before.queryForObject(
                    "INSERT INTO tenants (name, subdomain) VALUES ('Acme', 'acme') RETURNING id", UUID.class);
            UUID template = before.queryForObject(
                    "INSERT INTO pipeline_templates (tenant_id, name) VALUES (?, 'Engineering') RETURNING id",
                    UUID.class, company);
            UUID onTemplate = before.queryForObject("INSERT INTO job_vacancies (tenant_id, pipeline_template_id, "
                    + "auto_threshold, manual_threshold) VALUES (?, ?, 80, 70) RETURNING id",
                    UUID.class, company, template);
            UUID legacy = before.queryForObject(
                    "INSERT INTO job_vacancies (tenant_id) VALUES (?) RETURNING id", UUID.class, company);
            String stage = "INSERT INTO job_stages (tenant_id, job_id, position, stage_type, name, benchmark) "
                    + "VALUES (?, ?, ?, ?, ?, ?)";
            before.update(stage, company, onTemplate, 0, "POST_APPLICATION", "Applied", null);
            before.update(stage, company, onTemplate, 1, "INTERVIEW", "Technical interview", 75);
            before.update(stage, company, onTemplate, 2, "INTERVIEW", "Panel interview", 70);
            before.update(stage, company, onTemplate, 3, "ONBOARD", "Onboard", null);

            migrate(dataSource, "latest");

            String summary = "SELECT pipeline_template_name, stage_count, interview_round_count "
                    + "FROM job_vacancies WHERE id = ?";
            JdbcTemplate after = new JdbcTemplate(dataSource);
            assertThat(after.queryForMap(summary, onTemplate))
                    .containsEntry("pipeline_template_name", "Engineering")
                    .containsEntry("stage_count", 4)
                    .containsEntry("interview_round_count", 2);
            assertThat(after.queryForMap(summary, legacy))
                    .containsEntry("pipeline_template_name", null)
                    .containsEntry("stage_count", null)
                    .containsEntry("interview_round_count", null);
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

    private UUID newTenant() {
        String subdomain = "t-" + UUID.randomUUID().toString().substring(0, 8);
        return jdbc.queryForObject("INSERT INTO tenants (name, subdomain) VALUES (?, ?) RETURNING id",
                UUID.class, "Company " + subdomain, subdomain);
    }

    private UUID newVacancy(UUID tenantId) {
        return jdbc.queryForObject("INSERT INTO job_vacancies (tenant_id) VALUES (?) RETURNING id",
                UUID.class, tenantId);
    }

    private void insertStage(UUID tenantId, UUID jobId, int position, String type, String name, Integer benchmark) {
        jdbc.update("INSERT INTO job_stages (tenant_id, job_id, position, stage_type, name, benchmark) "
                + "VALUES (?, ?, ?, ?, ?, ?)", tenantId, jobId, position, type, name, benchmark);
    }

    private long stagesOf(UUID jobId) {
        return jdbc.queryForObject("SELECT count(*) FROM job_stages WHERE job_id = ?", Long.class, jobId);
    }
}
