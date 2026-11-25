-- ---------------------------------------------------------------------------
-- V26: What AI screening reads and writes (MRP-358).
--
--   1. candidate_cvs          the text of each CV, extracted once
--   2. applications           which CV the candidate applied with
--   3. application_scores     how an application scored
--   4. job_screening_criteria what a vacancy scores its applications on
--
-- Only the tables and columns land here. Extracting the text belongs to the AI
-- service (MRP-359), scoring to MRP-361, and editing the criteria to MRP-364.
-- ---------------------------------------------------------------------------

-- --------------------------------------------------------------- 1. CV text
--
--   extracted_text     the CV as plain text. Personal data: it is read by
--                      screening only and never returned to a recruiter.
--   extraction_status  PENDING          not extracted yet, or the AI service was
--                                       not reachable; tried again later
--                      EXTRACTED        extracted_text holds the CV
--                      EMPTY_OR_SCANNED read, but there is (almost) no text in
--                                       it: a scan, or a design exported as
--                                       outlines. Needs a person.
--                      FAILED           the file itself cannot be read
--   text_extracted_at  when the file was read
--
-- Every CV uploaded before this migration starts as PENDING.
ALTER TABLE candidate_cvs
    ADD COLUMN extracted_text    TEXT,
    ADD COLUMN extraction_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CONSTRAINT chk_candidate_cvs_extraction_status
        CHECK (extraction_status IN ('PENDING', 'EXTRACTED', 'EMPTY_OR_SCANNED', 'FAILED')),
    ADD COLUMN text_extracted_at TIMESTAMPTZ,

    -- Backstop: a CV that says it was extracted has its text.
    ADD CONSTRAINT chk_candidate_cvs_extracted
        CHECK (extraction_status <> 'EXTRACTED'
               OR (extracted_text IS NOT NULL AND text_extracted_at IS NOT NULL)),

    -- Target for the applications' foreign key below.
    ADD CONSTRAINT uq_candidate_cvs_id_candidate UNIQUE (id, candidate_id);

-- "Has this exact file been read before?" — the same file is extracted once.
CREATE INDEX idx_candidate_cvs_file_hash ON candidate_cvs (file_hash_sha256);

-- ------------------------------------------- 2. The CV an application was made with
--
-- A candidate can replace their CV at any time; an application keeps pointing
-- at the version that was active when it was submitted, so a later upload never
-- changes what an earlier application is judged on.
--
-- NULL when the candidate had no CV, and for every application made before this
-- migration: which CV those were made with was never recorded, and guessing
-- would put words in the candidate's mouth.
--
-- The foreign key is composite, as for vacancies (V18), so the database itself
-- keeps an application on its own candidate's CV. Deleting a CV clears only the
-- link: the column list is what stops SET NULL from also clearing candidate_id.
ALTER TABLE applications
    ADD COLUMN cv_id UUID,
    ADD CONSTRAINT fk_applications_cv FOREIGN KEY (cv_id, candidate_id)
        REFERENCES candidate_cvs (id, candidate_id) ON DELETE SET NULL (cv_id),

    -- Target for the scores' foreign key below.
    ADD CONSTRAINT uq_applications_id_tenant UNIQUE (id, tenant_id);

-- Backs the SET NULL above.
CREATE INDEX idx_applications_cv ON applications (cv_id) WHERE cv_id IS NOT NULL;

-- ------------------------------------------------------- 3. Application scores
--
-- One row per application: the result of its latest screening. Screening again
-- updates the row. No row means the application has not been screened.
CREATE TABLE application_scores (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    application_id   UUID         NOT NULL,
    tenant_id        UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SCORED'
                     CHECK (status IN ('PENDING', 'SCORED', 'SCORING_FAILED')),
    overall_score    INTEGER
                     CHECK (overall_score IS NULL OR (overall_score BETWEEN 0 AND 100)),
    summary          TEXT,
    -- Lists of strings, and of {name, score, rationale}: read and written whole,
    -- and nothing references their entries.
    strengths        JSONB        NOT NULL DEFAULT '[]',
    gaps             JSONB        NOT NULL DEFAULT '[]',
    criterion_scores JSONB        NOT NULL DEFAULT '[]',
    -- Which model and prompt produced the score, so one can be explained later.
    model_version    VARCHAR(80),
    prompt_version   VARCHAR(30),
    cached           BOOLEAN      NOT NULL DEFAULT false,
    failure_reason   VARCHAR(500),
    scored_at        TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    -- Screening an application twice must update its row, never add another.
    CONSTRAINT uq_application_scores_application UNIQUE (application_id),

    -- Same company as the application. Removing the application removes its score.
    CONSTRAINT fk_application_scores_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES applications (id, tenant_id) ON DELETE CASCADE,

    -- Backstop: a row that says SCORED has a score, and says where it came from.
    CONSTRAINT chk_application_scores_state
        CHECK (status <> 'SCORED'
               OR (overall_score IS NOT NULL AND summary IS NOT NULL
                   AND model_version IS NOT NULL AND scored_at IS NOT NULL)),

    CONSTRAINT chk_application_scores_lists
        CHECK (jsonb_typeof(strengths) = 'array'
               AND jsonb_typeof(gaps) = 'array'
               AND jsonb_typeof(criterion_scores) = 'array')
);

-- A company's applications, best first, unscored last.
CREATE INDEX idx_app_scores_tenant_overall
    ON application_scores (tenant_id, overall_score DESC NULLS LAST);

-- --------------------------------------------------- 4. Screening criteria
--
-- What a vacancy's applications are scored on, and how much each counts.
CREATE TABLE job_screening_criteria (
    id         UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id     UUID          NOT NULL,
    tenant_id  UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name       VARCHAR(120)  NOT NULL,
    weight     NUMERIC(4, 3) NOT NULL
               CHECK (weight > 0 AND weight <= 1),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- Also serves "a vacancy's criteria" and the cascade below.
    CONSTRAINT uq_criteria_job_name UNIQUE (job_id, name),

    CONSTRAINT fk_job_screening_criteria_job FOREIGN KEY (job_id, tenant_id)
        REFERENCES job_vacancies (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_job_screening_criteria_tenant ON job_screening_criteria (tenant_id);

-- ---------------------------------------------------- Row-Level Security
--
-- The same policy as job_stages (V23). candidate_cvs has none: candidates, and
-- so their CVs, belong to no company.
ALTER TABLE application_scores ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_application_scores ON application_scores
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

ALTER TABLE job_screening_criteria ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_job_screening_criteria ON job_screening_criteria
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
