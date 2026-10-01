-- ---------------------------------------------------------------------------
-- V11: Jobs and candidate applications.
-- ---------------------------------------------------------------------------

CREATE TABLE jobs (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL DEFAULT 'PUBLISHED'
                CHECK (status IN ('PUBLISHED', 'CLOSED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_jobs_public ON jobs (status, created_at);
CREATE INDEX idx_jobs_tenant ON jobs (tenant_id, status);

CREATE TABLE applications (
    id            UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id        UUID         NOT NULL REFERENCES jobs (id) ON DELETE CASCADE,
    candidate_id  UUID         NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    tenant_id     UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    status        VARCHAR(20)  NOT NULL DEFAULT 'APPLIED'
                  CHECK (status IN ('APPLIED', 'IN_REVIEW', 'SHORTLISTED', 'REJECTED', 'WITHDRAWN')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    withdrawn_at  TIMESTAMPTZ,
    CONSTRAINT uq_application_candidate_job UNIQUE (candidate_id, job_id)
);

CREATE INDEX idx_applications_candidate ON applications (candidate_id, created_at DESC);
CREATE INDEX idx_applications_tenant ON applications (tenant_id, status, created_at DESC);

ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'INVITATION', 'APPLICATION_WITHDRAWN'));
