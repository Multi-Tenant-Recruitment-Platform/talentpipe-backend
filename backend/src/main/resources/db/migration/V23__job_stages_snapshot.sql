-- ---------------------------------------------------------------------------
-- V23: A vacancy keeps its own copy of its pipeline.
--
-- When a vacancy is created from a pipeline template, the template's stages
-- are copied into job_stages and its screening settings onto the vacancy.
-- From then on the two are independent: editing, archiving or deleting the
-- template never changes a vacancy that already exists (PT-7, PT-8).
--
-- Rows are written once, when the vacancy is created, and never updated, so
-- job_stages has created_at but no updated_at.
-- ---------------------------------------------------------------------------

-- ----------------------------------------------------- vacancy-side settings
--
-- All four are nullable or defaulted: vacancies created before this migration
-- have no template, and keep working without one.
--
--   pipeline_template_id  the template the vacancy was created from. SET NULL,
--                         not CASCADE: deleting a template must never take a
--                         vacancy with it, and the stages are already copied.
--   auto_threshold /      the template's defaults unless overridden when the
--   manual_threshold      vacancy was created; same 0-100 scale and ordering
--                         rule as the template's (V15).
--   blind_mode_enabled    copied from the template unless overridden.
ALTER TABLE job_vacancies
    ADD COLUMN pipeline_template_id UUID
        CONSTRAINT fk_job_vacancies_pipeline_template
        REFERENCES pipeline_templates (id) ON DELETE SET NULL,
    ADD COLUMN auto_threshold       INTEGER
        CONSTRAINT chk_job_vacancies_auto_threshold CHECK (auto_threshold BETWEEN 0 AND 100),
    ADD COLUMN manual_threshold     INTEGER
        CONSTRAINT chk_job_vacancies_manual_threshold CHECK (manual_threshold BETWEEN 0 AND 100),
    ADD COLUMN blind_mode_enabled   BOOLEAN NOT NULL DEFAULT false,
    ADD CONSTRAINT chk_job_vacancies_thresholds
        CHECK (manual_threshold IS NULL OR auto_threshold IS NULL OR manual_threshold < auto_threshold);

-- Backs the SET NULL above: deleting a template looks up the vacancies that
-- reference it. Partial, because vacancies older than V23 never will.
CREATE INDEX idx_job_vacancies_pipeline_template
    ON job_vacancies (pipeline_template_id)
    WHERE pipeline_template_id IS NOT NULL;

-- ------------------------------------------------------------- stage copies
--
-- Same shape and rules as pipeline_template_stages (V15). The foreign key to
-- the vacancy is composite, as for the hiring team (V18), so the database
-- itself keeps a stage in the same company as its vacancy; removing the
-- vacancy removes its stages.
CREATE TABLE job_stages (
    id          UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    job_id      UUID          NOT NULL,
    position    INTEGER       NOT NULL CHECK (position >= 0),
    stage_type  VARCHAR(30)   NOT NULL
                CHECK (stage_type IN ('POST_APPLICATION', 'SCREENING', 'INTERVIEW', 'ONBOARD')),
    name        VARCHAR(100)  NOT NULL,
    benchmark   INTEGER
                CHECK (benchmark IS NULL OR (benchmark >= 0 AND benchmark <= 100)),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT fk_job_stages_job FOREIGN KEY (job_id, tenant_id)
        REFERENCES job_vacancies (id, tenant_id) ON DELETE CASCADE,

    -- Also serves "a vacancy's stages in order" and the cascade above.
    CONSTRAINT uq_job_stages_job_position
        UNIQUE (job_id, position),

    -- A benchmark belongs to an interview round, and every interview round has one.
    CONSTRAINT chk_job_stages_benchmark
        CHECK (
            (stage_type = 'INTERVIEW' AND benchmark IS NOT NULL) OR
            (stage_type <> 'INTERVIEW' AND benchmark IS NULL)
        )
);

CREATE INDEX idx_job_stages_tenant
    ON job_stages (tenant_id);

-- Row-Level Security, the same policy as pipeline_template_stages (V15).
ALTER TABLE job_stages ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_job_stages ON job_stages
    FOR ALL
    USING (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    )
    WITH CHECK (
        current_setting('app.current_tenant', true) IS NULL
        OR current_setting('app.current_tenant', true) = ''
        OR tenant_id = current_setting('app.current_tenant', true)::uuid
    );
