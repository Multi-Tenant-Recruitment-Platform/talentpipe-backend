-- =============================================================================
-- V29: what the recruiter's applicant dashboard runs on.
--
-- Three ideas are kept apart:
--   * the screening OUTCOME  — the AI's verdict on one score, fixed once made
--                               (application_scores.outcome);
--   * the REVIEW STATUS      — what the dashboard filters, counts and ranks on,
--                               set from the outcome and then by recruiters
--                               (applications.review_status);
--   * the CURRENT STAGE      — where the applicant is in that vacancy's
--                               pipeline (applications.current_stage_id).
-- applications.status (APPLIED … WITHDRAWN) is left as it is: the candidate's
-- own pages and the older endpoints read it.
--
-- Every step only adds. Existing rows are backfilled from what they already
-- say; nothing is deleted or rewritten.
-- =============================================================================

-- --- Stages: a key other tables can reference with their tenant ---------------
-- Lets an application point at a stage of its own company only (composite FK).
ALTER TABLE job_stages
    ADD CONSTRAINT uq_job_stages_id_tenant UNIQUE (id, tenant_id);

-- --- Applications --------------------------------------------------------------
ALTER TABLE applications
    ADD COLUMN review_status    VARCHAR(30) NOT NULL DEFAULT 'PENDING_SCREENING'
        CONSTRAINT chk_applications_review_status
        CHECK (review_status IN ('PENDING_SCREENING', 'AUTO_ADVANCED', 'MANUAL_REVIEW',
                                 'ADVANCED_BY_RECRUITER', 'REJECTED', 'WITHDRAWN')),
    ADD COLUMN current_stage_id UUID,
    -- Optimistic locking for recruiter actions and the screener racing each other.
    ADD COLUMN version          BIGINT      NOT NULL DEFAULT 0,
    -- The latest recruiter decision (shortlist, reject, advance); NULL until one.
    ADD COLUMN decided_at       TIMESTAMPTZ,
    ADD COLUMN decided_by       UUID
        CONSTRAINT fk_applications_decided_by REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT fk_applications_current_stage FOREIGN KEY (current_stage_id, tenant_id)
        REFERENCES job_stages (id, tenant_id) ON DELETE SET NULL (current_stage_id);

-- What each existing application already says about itself. Recruiter
-- decisions carry over; everything else waits to be screened.
UPDATE applications
SET review_status = CASE status
        WHEN 'REJECTED'    THEN 'REJECTED'
        WHEN 'WITHDRAWN'   THEN 'WITHDRAWN'
        WHEN 'SHORTLISTED' THEN 'ADVANCED_BY_RECRUITER'
        ELSE 'PENDING_SCREENING'
    END;

-- Applicants of a vacancy that runs on a pipeline start at its first stage.
-- Vacancies filed before pipelines (no job_stages) keep NULL.
UPDATE applications a
SET current_stage_id = s.id
FROM job_stages s
WHERE s.job_id = a.job_id
  AND s.tenant_id = a.tenant_id
  AND s.stage_type = 'POST_APPLICATION';

-- The dashboard reads one vacancy's applicants, grouped by review status.
CREATE INDEX idx_applications_job_review
    ON applications (tenant_id, job_id, review_status);
CREATE INDEX idx_applications_job_created
    ON applications (tenant_id, job_id, created_at);

-- --- Scores: the verdict and the rule that produced it -------------------------
ALTER TABLE application_scores
    ADD COLUMN outcome               VARCHAR(20)
        CONSTRAINT chk_application_scores_outcome_value
        CHECK (outcome IS NULL OR outcome IN ('AUTO_ADVANCED', 'MANUAL_REVIEW', 'AUTO_REJECTED')),
    -- The thresholds the outcome was decided against, kept with it: a vacancy's
    -- thresholds can change later, and the verdict must still explain itself.
    ADD COLUMN auto_threshold_used   INTEGER
        CONSTRAINT chk_application_scores_auto_used
        CHECK (auto_threshold_used IS NULL OR auto_threshold_used BETWEEN 0 AND 100),
    ADD COLUMN manual_threshold_used INTEGER
        CONSTRAINT chk_application_scores_manual_used
        CHECK (manual_threshold_used IS NULL OR manual_threshold_used BETWEEN 0 AND 100),
    ADD COLUMN matched_skills        JSONB NOT NULL DEFAULT '[]',
    ADD COLUMN missing_skills        JSONB NOT NULL DEFAULT '[]',
    ADD CONSTRAINT chk_application_scores_skill_lists
        CHECK (jsonb_typeof(matched_skills) = 'array' AND jsonb_typeof(missing_skills) = 'array');

-- The verdict and its thresholds are written together or not at all. A SCORED
-- row may lack them: rows scored before V29 never had thresholds to give.
ALTER TABLE application_scores
    ADD CONSTRAINT chk_application_scores_outcome_thresholds
        CHECK ((outcome IS NULL AND auto_threshold_used IS NULL AND manual_threshold_used IS NULL)
               OR (outcome IS NOT NULL AND auto_threshold_used IS NOT NULL
                   AND manual_threshold_used IS NOT NULL
                   AND manual_threshold_used < auto_threshold_used));

-- --- Stage history: what happened to an application, in order ----------------
-- Append-only. Stage ids are kept without the tenant pair on purpose: the row's
-- own tenant_id already ties it to one company, and a history line must survive
-- whatever later happens to the stage.
CREATE TABLE application_stage_events (
    id                 UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id          UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    application_id     UUID         NOT NULL,
    event_type         VARCHAR(30)  NOT NULL
        CONSTRAINT chk_stage_events_type
        CHECK (event_type IN ('SUBMITTED', 'AI_SCREENED', 'SCREENING_FAILED', 'SCREENING_SKIPPED',
                              'ADVANCED', 'SHORTLISTED', 'STAGE_CHANGED', 'STATUS_CHANGED',
                              'REJECTED', 'HIRED', 'WITHDRAWN')),
    from_stage_id      UUID         REFERENCES job_stages (id) ON DELETE SET NULL,
    to_stage_id        UUID         REFERENCES job_stages (id) ON DELETE SET NULL,
    from_review_status VARCHAR(30),
    to_review_status   VARCHAR(30),
    -- NULL: the system did it (submission, screening).
    actor_user_id      UUID         REFERENCES users (id) ON DELETE SET NULL,
    reason             VARCHAR(500),
    metadata           JSONB        NOT NULL DEFAULT '{}'
        CONSTRAINT chk_stage_events_metadata CHECK (jsonb_typeof(metadata) = 'object'),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT fk_stage_events_application FOREIGN KEY (application_id, tenant_id)
        REFERENCES applications (id, tenant_id) ON DELETE CASCADE
);

CREATE INDEX idx_stage_events_application ON application_stage_events (application_id, created_at);
CREATE INDEX idx_stage_events_tenant ON application_stage_events (tenant_id);

-- Every existing application was submitted, at the time it was created …
INSERT INTO application_stage_events (tenant_id, application_id, event_type, to_stage_id,
                                      to_review_status, metadata, created_at)
SELECT a.tenant_id, a.id, 'SUBMITTED', a.current_stage_id, 'PENDING_SCREENING',
       '{"backfilled": true}'::jsonb, a.created_at
FROM applications a;

-- … and the decisions already taken are on record, dated when the row last changed.
INSERT INTO application_stage_events (tenant_id, application_id, event_type, from_review_status,
                                      to_review_status, metadata, created_at)
SELECT a.tenant_id, a.id,
       CASE a.status WHEN 'SHORTLISTED' THEN 'SHORTLISTED' ELSE a.status END,
       'PENDING_SCREENING', a.review_status,
       '{"backfilled": true}'::jsonb, a.updated_at
FROM applications a
WHERE a.status IN ('SHORTLISTED', 'REJECTED', 'WITHDRAWN');

ALTER TABLE application_stage_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_application_stage_events ON application_stage_events
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
