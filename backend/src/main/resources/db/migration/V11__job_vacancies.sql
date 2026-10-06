-- ---------------------------------------------------------------------------
-- V11: Job Vacancies (PB-011, PB-018 -> PB-022)
-- ---------------------------------------------------------------------------

CREATE TABLE job_vacancies (
    id                        UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id                 UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    version                   INTEGER       NOT NULL DEFAULT 0,
    status                    VARCHAR(20)   NOT NULL
        CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),

    -- Section 01: Basic information
    title                     VARCHAR(120)  NOT NULL DEFAULT '',
    department                VARCHAR(120)  NOT NULL DEFAULT '',
    openings                  INTEGER       NOT NULL DEFAULT 1
        CHECK (openings BETWEEN 1 AND 999),
    employment_type           VARCHAR(20),
    workplace_type            VARCHAR(20),
    location                  VARCHAR(120)  NOT NULL DEFAULT '',
    application_deadline      DATE,

    -- Section 02: Job description
    job_summary               VARCHAR(300)  NOT NULL DEFAULT '',
    job_description           TEXT          NOT NULL DEFAULT '',
    key_responsibilities      JSONB         NOT NULL DEFAULT '[]',

    -- Section 03: Candidate requirements
    required_skills           JSONB         NOT NULL DEFAULT '[]',
    preferred_skills          JSONB         NOT NULL DEFAULT '[]',
    minimum_experience_years  INTEGER       CHECK (minimum_experience_years BETWEEN 0 AND 50),
    education                 VARCHAR(120),
    certifications            JSONB         NOT NULL DEFAULT '[]',
    language_requirements     JSONB         NOT NULL DEFAULT '[]',
    other_requirements        VARCHAR(1000),

    -- Section 04: Salary & benefits
    salary_min                NUMERIC(14,2) CHECK (salary_min >= 0),
    salary_max                NUMERIC(14,2) CHECK (salary_max >= 0),
    currency                  VARCHAR(64),
    pay_period                VARCHAR(20),
    benefits                  JSONB         NOT NULL DEFAULT '[]',

    -- Section 05: Work schedule
    working_days              JSONB         NOT NULL DEFAULT '[]',
    working_hours             VARCHAR(60),
    shift_type                VARCHAR(20),
    expected_hours_per_week   NUMERIC(5,2)
        CHECK (expected_hours_per_week > 0 AND expected_hours_per_week <= 168),

    -- Section 06: Recruitment settings
    assigned_recruiter_id     UUID          REFERENCES users (id) ON DELETE SET NULL,
    hiring_manager_id         UUID          REFERENCES users (id) ON DELETE SET NULL,
    recruitment_pipeline_id   VARCHAR(40),
    screening_questions       JSONB         NOT NULL DEFAULT '[]',

    -- Timestamps
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at              TIMESTAMPTZ,
    closed_at                 TIMESTAMPTZ,
    archived_at               TIMESTAMPTZ
);

-- Every query is tenant-scoped; list sorts newest first
CREATE INDEX idx_job_vacancies_tenant_created
    ON job_vacancies (tenant_id, created_at DESC);

-- Public board: PUBLISHED only
CREATE INDEX idx_job_vacancies_published
    ON job_vacancies (status, application_deadline)
    WHERE status = 'PUBLISHED';
