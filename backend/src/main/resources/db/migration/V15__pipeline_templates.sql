-- ---------------------------------------------------------------------------
-- V15: Pipeline templates and stages.
--
-- Enables company admins to define, edit, archive and duplicate recruitment
-- pipeline templates and their stage configurations.
-- ---------------------------------------------------------------------------

CREATE TABLE pipeline_templates (
    id                        UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id                 UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name                      VARCHAR(120)  NOT NULL,
    description               VARCHAR(1000),
    default_auto_threshold    INTEGER       NOT NULL DEFAULT 80
                              CHECK (default_auto_threshold BETWEEN 0 AND 100),
    default_manual_threshold  INTEGER       NOT NULL DEFAULT 70
                              CHECK (default_manual_threshold BETWEEN 0 AND 100),
    ideal_matching_enabled    BOOLEAN       NOT NULL DEFAULT false,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE'
                              CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version                   INTEGER       NOT NULL DEFAULT 0,
    created_by                UUID          REFERENCES users (id) ON DELETE SET NULL,
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    archived_at               TIMESTAMPTZ,

    CONSTRAINT chk_pipeline_templates_thresholds
        CHECK (default_manual_threshold < default_auto_threshold)
);

-- Active template names must be unique per tenant (case-insensitive)
CREATE UNIQUE INDEX uq_pipeline_templates_tenant_name_active
    ON pipeline_templates (tenant_id, lower(name))
    WHERE status = 'ACTIVE';

CREATE INDEX idx_pipeline_templates_tenant_status
    ON pipeline_templates (tenant_id, status, created_at DESC);

-- Stages for each pipeline template
CREATE TABLE pipeline_template_stages (
    id          UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    template_id UUID          NOT NULL REFERENCES pipeline_templates (id) ON DELETE CASCADE,
    position    INTEGER       NOT NULL CHECK (position >= 0),
    stage_type  VARCHAR(30)   NOT NULL
                CHECK (stage_type IN ('POST_APPLICATION', 'SCREENING', 'INTERVIEW', 'ONBOARD')),
    name        VARCHAR(100)  NOT NULL,
    benchmark   INTEGER
                CHECK (benchmark IS NULL OR (benchmark >= 0 AND benchmark <= 100)),
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uq_pipeline_template_stages_pos
        UNIQUE (template_id, position),

    CONSTRAINT chk_pipeline_template_stages_benchmark
        CHECK (
            (stage_type = 'INTERVIEW' AND benchmark IS NOT NULL) OR
            (stage_type <> 'INTERVIEW' AND benchmark IS NULL)
        )
);

CREATE INDEX idx_pipeline_template_stages_template
    ON pipeline_template_stages (template_id, position ASC);

CREATE INDEX idx_pipeline_template_stages_tenant
    ON pipeline_template_stages (tenant_id);

-- Row-Level Security
ALTER TABLE pipeline_templates ENABLE ROW LEVEL SECURITY;
ALTER TABLE pipeline_template_stages ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_pipeline_templates ON pipeline_templates
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

CREATE POLICY tenant_isolation_pipeline_template_stages ON pipeline_template_stages
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
