-- ---------------------------------------------------------------------------
-- V27: Ideal candidate matching — what the person creating a vacancy says the
-- ideal candidate looks like, and how much each part counts.
--
--   1. job_vacancies           a new candidate requirement (tools and
--                              technologies), whether matching is offered and
--                              switched on, and the two unweighted lists
--   2. job_screening_criteria  the weighted criteria (V26), now with a kind,
--                              a must-have / preferred flag and an order
--
-- The weighted criteria share one 100%: each technical skill, each tool and
-- relevant experience carry their own share. Education and certifications are
-- never weighted; they travel to screening as context only.
--
-- A draft may hold any total while it is being put together. Publishing needs
-- exactly 100%; that rule is the service's, as for interview panels (V25).
-- Nothing here calls the AI service: the backend only stores the profile and
-- hands it out (MRP-361 scores applications with it).
-- ---------------------------------------------------------------------------

-- ------------------------------------------------------------ 1. Vacancies
--
--   tools_and_technologies   candidate requirement, public like the skills
--                            (Docker, Git, AWS, ...)
--   ideal_matching_available whether the vacancy's pipeline template offers
--                            matching (pipeline_templates.ideal_matching_enabled),
--                            copied when the vacancy is created so a later edit
--                            or deletion of the template changes nothing
--   ideal_matching_enabled   the vacancy's own switch; only where offered
--   matching_degree_types    accepted degree types (BSc Computer Science, ...),
--                            when the education asked for is a degree
--   matching_nice_to_have_certifications
--                            certifications that count in a candidate's favour
ALTER TABLE job_vacancies
    ADD COLUMN tools_and_technologies               TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN ideal_matching_available             BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ideal_matching_enabled               BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN matching_degree_types                TEXT[]  NOT NULL DEFAULT '{}',
    ADD COLUMN matching_nice_to_have_certifications TEXT[]  NOT NULL DEFAULT '{}',
    ADD CONSTRAINT chk_job_vacancies_ideal_matching
        CHECK (NOT ideal_matching_enabled OR ideal_matching_available);

-- Vacancies created before this migration offer matching if their template does.
UPDATE job_vacancies v
SET ideal_matching_available = t.ideal_matching_enabled
FROM pipeline_templates t
WHERE v.pipeline_template_id = t.id;

-- ------------------------------------------------- 2. Weighted criteria
--
--   category     TECHNICAL_SKILL, TOOL or EXPERIENCE. Education and
--                certifications are deliberately not here: they are never weighted.
--   requirement  MUST_HAVE or PREFERRED for skills and tools; none for experience.
--   position     the order the recruiter put them in.
--
-- The weight keeps V26's scale: a fraction above 0 and at most 1 (25% is 0.250),
-- the shape the AI service reads. A row written without a kind — every row
-- before V27, and any written the V26 way — is a preferred technical skill.
ALTER TABLE job_screening_criteria
    ADD COLUMN category    VARCHAR(20) NOT NULL DEFAULT 'TECHNICAL_SKILL'
        CONSTRAINT chk_criteria_category CHECK (category IN ('TECHNICAL_SKILL', 'TOOL', 'EXPERIENCE')),
    ADD COLUMN requirement VARCHAR(20) DEFAULT 'PREFERRED'
        CONSTRAINT chk_criteria_requirement CHECK (requirement IN ('MUST_HAVE', 'PREFERRED')),
    ADD COLUMN position    INTEGER     NOT NULL DEFAULT 0
        CONSTRAINT chk_criteria_position CHECK (position >= 0);

-- A draft may hold a criterion whose weight is not decided yet; publishing
-- needs every weight. V26's range check still applies to every weight given.
ALTER TABLE job_screening_criteria ALTER COLUMN weight DROP NOT NULL;

-- Skills and tools say whether they are a must; experience is one share, not a
-- must. The application always writes both columns, so an experience row gets
-- its NULL explicitly rather than the default.
ALTER TABLE job_screening_criteria
    ADD CONSTRAINT chk_criteria_requirement_kind
        CHECK ((category = 'EXPERIENCE' AND requirement IS NULL)
            OR (category <> 'EXPERIENCE' AND requirement IS NOT NULL));

-- The AI service refuses two criteria whose names differ only in case, so
-- "Git" cannot be both a skill and a tool. Replaces V26's case-sensitive key.
ALTER TABLE job_screening_criteria DROP CONSTRAINT uq_criteria_job_name;
CREATE UNIQUE INDEX uq_criteria_job_name_ci ON job_screening_criteria (job_id, lower(name));

-- Relevant experience is a single share.
CREATE UNIQUE INDEX uq_criteria_job_experience
    ON job_screening_criteria (job_id) WHERE category = 'EXPERIENCE';
