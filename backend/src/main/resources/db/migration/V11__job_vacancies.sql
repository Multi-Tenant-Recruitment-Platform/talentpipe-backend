-- ---------------------------------------------------------------------------
-- V11: Job vacancies — lifecycle (PB-018 → PB-022) and public search (PB-017).
--
-- One row per vacancy. A vacancy moves one way through
--
--     DRAFT ──publish──▶ PUBLISHED ──close──▶ CLOSED ──archive──▶ ARCHIVED
--
-- and is never deleted: archived rows stay queryable so reports keep their
-- history. The application enforces the transitions (VacancyStatus /
-- JobVacancy); the CHECK constraints below are the backstop that keeps a row
-- written any other way (SQL console, a future bulk import) from holding a
-- state the state machine could never have produced.
--
-- List fields are TEXT[] for the same reason as the tenant profile lists in
-- V9: they are read and written whole and nothing references their entries.
-- ---------------------------------------------------------------------------

-- Trigram matching backs the public board's "location contains" filter.
-- pg_trgm ships with PostgreSQL and is a trusted extension (13+).
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- Search document.
--
-- Builds the weighted full-text vector the public board's keyword search runs
-- against. Weights rank a hit in the title above the same word in the body:
--
--     A  title
--     B  required skills, preferred skills, department
--     C  job summary, location
--     D  job description, key responsibilities
--
-- Each field is indexed twice: as written, and with punctuation replaced by
-- spaces. The text-search parser keeps "Node.js" and "CI/CD" as single tokens,
-- so without the second copy a search for "node" or "ci" would miss them; with
-- only the second copy a search for "node.js" would.
--
-- Declared IMMUTABLE so it can drive a generated column. array_to_string is
-- formally STABLE (an element type's output function could depend on session
-- settings); for TEXT[] it is not, so the declaration is safe. PostgreSQL
-- deliberately does not inline a function declared IMMUTABLE whose body is
-- not, which is what keeps this wrapper intact.
--
-- If this function's definition ever changes, existing rows keep the vector
-- they were stored with. Follow the change with
--     UPDATE job_vacancies SET title = title;
-- to recompute them.
-- ---------------------------------------------------------------------------
CREATE FUNCTION job_vacancy_search_vector(
    p_title                TEXT,
    p_department           TEXT,
    p_location             TEXT,
    p_job_summary          TEXT,
    p_job_description      TEXT,
    p_key_responsibilities TEXT[],
    p_required_skills      TEXT[],
    p_preferred_skills     TEXT[]
) RETURNS tsvector
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
AS $$
    SELECT setweight(to_tsvector('english'::regconfig, src.title), 'A')
        || setweight(to_tsvector('english'::regconfig, src.skills), 'B')
        || setweight(to_tsvector('english'::regconfig, src.summary), 'C')
        || setweight(to_tsvector('english'::regconfig, src.body), 'D')
    FROM (
        SELECT raw.title   || ' ' || regexp_replace(raw.title,   '[^[:alnum:]]+', ' ', 'g') AS title,
               raw.skills  || ' ' || regexp_replace(raw.skills,  '[^[:alnum:]]+', ' ', 'g') AS skills,
               raw.summary || ' ' || regexp_replace(raw.summary, '[^[:alnum:]]+', ' ', 'g') AS summary,
               raw.body    || ' ' || regexp_replace(raw.body,    '[^[:alnum:]]+', ' ', 'g') AS body
        FROM (
            SELECT coalesce(p_title, '') AS title,
                   coalesce(array_to_string(p_required_skills, ' '), '') || ' ' ||
                   coalesce(array_to_string(p_preferred_skills, ' '), '') || ' ' ||
                   coalesce(p_department, '') AS skills,
                   coalesce(p_job_summary, '') || ' ' || coalesce(p_location, '') AS summary,
                   coalesce(p_job_description, '') || ' ' ||
                   coalesce(array_to_string(p_key_responsibilities, ' '), '') AS body
        ) raw
    ) src
$$;

-- ---------------------------------------------------------------------------
-- Vacancies.
--
-- The six "basics" (title, department, location, job_summary, job_description
-- and the deadline) are optional on a DRAFT and required to publish, so text
-- columns default to '' rather than NULL: a draft is a partial vacancy, and
-- "nothing typed yet" is not a different fact from "typed nothing".
-- ---------------------------------------------------------------------------
CREATE TABLE job_vacancies (
    id                        UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id                 UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    -- Optimistic-lock counter (JPA @Version). Echoed by the client on PUT so a
    -- save that lost a race answers 409 instead of overwriting the winner.
    version                   INTEGER       NOT NULL DEFAULT 0,
    status                    VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
                              CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),

    -- Basic information
    title                     VARCHAR(120)  NOT NULL DEFAULT '',
    department                VARCHAR(120)  NOT NULL DEFAULT '',
    openings                  INTEGER       NOT NULL DEFAULT 1
                              CHECK (openings BETWEEN 1 AND 999),
    employment_type           VARCHAR(20)
                              CHECK (employment_type IN ('FULL_TIME', 'PART_TIME', 'CONTRACT',
                                                         'INTERNSHIP', 'TEMPORARY')),
    workplace_type            VARCHAR(20)
                              CHECK (workplace_type IN ('ON_SITE', 'REMOTE', 'HYBRID')),
    location                  VARCHAR(120)  NOT NULL DEFAULT '',
    -- A calendar day, not an instant: applications are accepted through the
    -- end of this day in the tenant's timezone.
    application_deadline      DATE,

    -- Job description
    job_summary               VARCHAR(300)  NOT NULL DEFAULT '',
    job_description           TEXT          NOT NULL DEFAULT '',
    key_responsibilities      TEXT[]        NOT NULL DEFAULT '{}',

    -- Candidate requirements
    required_skills           TEXT[]        NOT NULL DEFAULT '{}',
    preferred_skills          TEXT[]        NOT NULL DEFAULT '{}',
    minimum_experience_years  INTEGER
                              CHECK (minimum_experience_years BETWEEN 0 AND 50),
    education                 VARCHAR(120),
    certifications            TEXT[]        NOT NULL DEFAULT '{}',
    language_requirements     TEXT[]        NOT NULL DEFAULT '{}',
    other_requirements        VARCHAR(1000),

    -- Salary & benefits
    salary_min                NUMERIC(14, 2) CHECK (salary_min >= 0),
    salary_max                NUMERIC(14, 2) CHECK (salary_max >= 0),
    currency                  VARCHAR(64),
    pay_period                VARCHAR(20)
                              CHECK (pay_period IN ('HOURLY', 'MONTHLY', 'ANNUAL')),
    benefits                  TEXT[]        NOT NULL DEFAULT '{}',

    -- Work schedule
    working_days              TEXT[]        NOT NULL DEFAULT '{}',
    working_hours             VARCHAR(60),
    shift_type                VARCHAR(20)
                              CHECK (shift_type IN ('DAY', 'NIGHT', 'ROTATING', 'FLEXIBLE')),
    expected_hours_per_week   NUMERIC(5, 2)
                              CHECK (expected_hours_per_week > 0 AND expected_hours_per_week <= 168),

    -- Recruitment settings. SET NULL rather than CASCADE: losing a team member
    -- must never take the vacancies they were assigned to with them.
    assigned_recruiter_id     UUID          REFERENCES users (id) ON DELETE SET NULL,
    hiring_manager_id         UUID          REFERENCES users (id) ON DELETE SET NULL,
    recruitment_pipeline_id   VARCHAR(40),
    screening_questions       TEXT[]        NOT NULL DEFAULT '{}',

    -- Lifecycle timestamps: set on entering each state, never cleared.
    created_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at              TIMESTAMPTZ,
    closed_at                 TIMESTAMPTZ,
    archived_at               TIMESTAMPTZ,

    -- Keyword-search document. Generated, so it is rewritten in the same
    -- transaction as the row and can never lag behind an edit — which is what
    -- makes a saved change show up in public search immediately. Not mapped
    -- on the JPA entity; only the native search query reads it.
    search_vector             tsvector      GENERATED ALWAYS AS (
        job_vacancy_search_vector(title, department, location, job_summary, job_description,
                                  key_responsibilities, required_skills, preferred_skills)
    ) STORED,

    CONSTRAINT chk_job_vacancies_salary_range
        CHECK (salary_min IS NULL OR salary_max IS NULL OR salary_max >= salary_min),

    -- The only route to each state runs through the ones before it, so a row's
    -- status implies which lifecycle timestamps must already be set.
    CONSTRAINT chk_job_vacancies_published_at
        CHECK (status = 'DRAFT' OR published_at IS NOT NULL),
    CONSTRAINT chk_job_vacancies_closed_at
        CHECK (status NOT IN ('CLOSED', 'ARCHIVED') OR closed_at IS NOT NULL),
    CONSTRAINT chk_job_vacancies_archived_at
        CHECK (status <> 'ARCHIVED' OR archived_at IS NOT NULL)
);

-- ---------------------------------------------------------------------------
-- Dashboard indexes. Every recruiter-side query is scoped to one tenant.
-- ---------------------------------------------------------------------------

-- GET /jobs: a tenant's vacancies, newest first.
CREATE INDEX idx_job_vacancies_tenant_created ON job_vacancies (tenant_id, created_at DESC);

-- GET /jobs?status=… and GET /jobs/counts (per-status totals for reporting).
CREATE INDEX idx_job_vacancies_tenant_status ON job_vacancies (tenant_id, status);

-- ---------------------------------------------------------------------------
-- Public board indexes.
--
-- All four are PARTIAL on status = 'PUBLISHED'. The board only ever reads live
-- adverts, so drafts and the ever-growing pile of closed and archived rows
-- cost these indexes nothing: they stay the size of what is currently open,
-- and a draft being edited never touches them. The search query spells the
-- status as a literal (not a bind parameter) so the planner can always prove
-- the predicate and use them.
--
-- The two GIN indexes set fastupdate = off. By default GIN defers new entries
-- to an unsorted "pending list" that is only merged in by VACUUM; until then
-- every search must scan that list in full, and the planner prices the index
-- accordingly — measured here, a freshly loaded table of 5,000 adverts was
-- searched by sequential scan because the whole index was still pending.
-- Writing entries straight into the index costs a little on each publish or
-- edit of a live advert, which is rare, and keeps every search — which is
-- constant — on the index. The right trade for a read-heavy job board.
-- ---------------------------------------------------------------------------

-- Browsing with no keyword: newest first, id as the tie-break that keeps
-- pagination stable. Matches the ORDER BY exactly, so no sort is needed.
CREATE INDEX idx_job_vacancies_public_recent
    ON job_vacancies (published_at DESC, id)
    WHERE status = 'PUBLISHED';

-- Keyword search: search_vector @@ websearch_to_tsquery(...).
CREATE INDEX idx_job_vacancies_public_search
    ON job_vacancies USING GIN (search_vector)
    WITH (fastupdate = off)
    WHERE status = 'PUBLISHED';

-- Category filter: case-insensitive equality on department. The trailing
-- columns repeat the board's ORDER BY, so one category's adverts are read off
-- the index already newest-first.
CREATE INDEX idx_job_vacancies_public_category
    ON job_vacancies (lower(department), published_at DESC, id)
    WHERE status = 'PUBLISHED';

-- Location filter: case-insensitive "contains" (lower(location) LIKE '%…%').
-- A b-tree cannot serve a pattern with a leading wildcard; a trigram GIN can.
CREATE INDEX idx_job_vacancies_public_location
    ON job_vacancies USING GIN (lower(location) gin_trgm_ops)
    WITH (fastupdate = off)
    WHERE status = 'PUBLISHED';
