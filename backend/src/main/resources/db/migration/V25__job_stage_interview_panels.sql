-- ---------------------------------------------------------------------------
-- V25: Who interviews in each interview round of a vacancy.
--
-- Every interview stage of a vacancy (job_stages, V23) can have a panel: how
-- many interviewers the round needs, and which company members they are.
-- Anyone active in the company can sit on a panel whatever their role — an
-- Owner, a Recruiter, an Interviewer, a custom role. Candidates are not users
-- (they live in the candidate module), so they can never be chosen.
--
-- job_stages is written once and never changed, so the panel lives beside it
-- rather than in it: the stage says what the round is, the panel says who runs
-- it, and only the panel changes while the vacancy is worked on.
--
-- A vacancy can be saved as a draft with a panel still being put together;
-- publishing it needs every interview round to have exactly as many
-- interviewers as its panel asks for. That rule is the service's: here, a
-- panel only ever holds a sensible number.
--
-- Being on a panel is another way onto a vacancy, after the four V18 lists:
-- a panel member whose role lacks jobs.view.all can still see that vacancy.
-- What they may do with it is still decided by their role.
--
-- Later sprints schedule each candidate's interview with people chosen from
-- this panel; those rows will reference job_stage_interviewers.
-- ---------------------------------------------------------------------------

-- Targets for the composite foreign key below. stage_type is in it so the
-- database itself keeps a panel off a stage that is not an interview round.
ALTER TABLE job_stages
    ADD CONSTRAINT uq_job_stages_id_job_tenant_type UNIQUE (id, job_id, tenant_id, stage_type);

-- ------------------------------------------------------------------- panels
--
-- One row per interview stage that has a panel. No row means nobody has been
-- chosen yet. stage_type is always INTERVIEW; it is stored only so the
-- foreign key can check it.
CREATE TABLE job_stage_panels (
    job_stage_id      UUID        PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    job_id            UUID        NOT NULL,
    stage_type        VARCHAR(30) NOT NULL DEFAULT 'INTERVIEW'
                      CONSTRAINT chk_job_stage_panels_interview CHECK (stage_type = 'INTERVIEW'),
    interviewer_count INTEGER     NOT NULL
                      CONSTRAINT chk_job_stage_panels_count CHECK (interviewer_count BETWEEN 1 AND 10),
    -- SET NULL for the same reason as created_by (V18): losing a person must
    -- never take a vacancy's set-up with them.
    updated_by        UUID        REFERENCES users (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Same vacancy, same company, and an interview round. Removing the vacancy
    -- removes its stages, and with them their panels.
    CONSTRAINT fk_job_stage_panels_stage FOREIGN KEY (job_stage_id, job_id, tenant_id, stage_type)
        REFERENCES job_stages (id, job_id, tenant_id, stage_type) ON DELETE CASCADE,

    -- Target for the interviewers' foreign key below.
    CONSTRAINT uq_job_stage_panels_stage_job_tenant UNIQUE (job_stage_id, job_id, tenant_id)
);

-- "A vacancy's panels" — every read of them is by vacancy.
CREATE INDEX idx_job_stage_panels_job ON job_stage_panels (job_id);

-- ------------------------------------------------------------- interviewers
--
-- One row per person per panel. job_id is carried so "which vacancies is
-- this person on a panel of?" — the visibility rule — needs no join.
CREATE TABLE job_stage_interviewers (
    job_stage_id UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    tenant_id    UUID        NOT NULL,
    job_id       UUID        NOT NULL,
    added_by     UUID        REFERENCES users (id) ON DELETE SET NULL,
    added_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (job_stage_id, user_id),

    CONSTRAINT fk_job_stage_interviewers_panel FOREIGN KEY (job_stage_id, job_id, tenant_id)
        REFERENCES job_stage_panels (job_stage_id, job_id, tenant_id) ON DELETE CASCADE,
    -- Removing a person from the company removes them from its panels, as from
    -- its hiring teams (V18).
    CONSTRAINT fk_job_stage_interviewers_user FOREIGN KEY (user_id, tenant_id)
        REFERENCES users (id, tenant_id) ON DELETE CASCADE
);

-- "Which vacancies is this person on a panel of?" — the scoped dashboard list.
CREATE INDEX idx_job_stage_interviewers_user ON job_stage_interviewers (user_id, job_id);
-- "A vacancy's interviewers", for reading the panels back.
CREATE INDEX idx_job_stage_interviewers_job ON job_stage_interviewers (job_id);

-- ----------------------------------------------------- member name search
--
-- The panel picker searches the company's people by the first letters of a
-- first name, last name or email. Prefix LIKE on lower(...) can use these.
CREATE INDEX idx_users_tenant_first_name_prefix
    ON users (tenant_id, lower(first_name) text_pattern_ops);
CREATE INDEX idx_users_tenant_last_name_prefix
    ON users (tenant_id, lower(last_name) text_pattern_ops);
CREATE INDEX idx_users_tenant_email_prefix
    ON users (tenant_id, lower(email) text_pattern_ops);

-- ---------------------------------------------------- Row-Level Security
--
-- The same policy as job_stages (V23).
ALTER TABLE job_stage_panels ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_job_stage_panels ON job_stage_panels
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

ALTER TABLE job_stage_interviewers ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_job_stage_interviewers ON job_stage_interviewers
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
