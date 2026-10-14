-- ---------------------------------------------------------------------------
-- V13: Candidate profile expansion, CV tracking and Talent Pool audit.
--
-- 1. Alters 'candidates' table to add profile photo and talent pool status.
-- 2. Creates 'candidate_cvs' for versioned resume storage and tracking.
-- 3. Creates 'candidate_talent_pool_audits' to record when candidates opt in/out
--    and what CV was attached at that time.
-- ---------------------------------------------------------------------------

-- 1. Extend candidates table
ALTER TABLE candidates
    ADD COLUMN photo_url      TEXT,
    ADD COLUMN in_talent_pool BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_candidates_talent_pool
    ON candidates (in_talent_pool)
    WHERE in_talent_pool = true;

-- 2. Candidate CVs table (Versioned tracking)
CREATE TABLE candidate_cvs (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    candidate_id     UUID         NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    file_name        VARCHAR(255) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    storage_url      TEXT,
    mime_type        VARCHAR(100) NOT NULL,
    file_size_bytes  BIGINT       NOT NULL,
    file_hash_sha256 VARCHAR(64)  NOT NULL,
    version          INTEGER      NOT NULL DEFAULT 1,
    is_active        BOOLEAN      NOT NULL DEFAULT true,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    uploaded_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    archived_at      TIMESTAMPTZ,
    CONSTRAINT chk_cv_format CHECK (mime_type IN (
        'application/pdf',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
    )),
    CONSTRAINT chk_cv_size CHECK (file_size_bytes > 0 AND file_size_bytes <= 5242880) -- 5 MB limit
);

-- Exactly one active CV per candidate
CREATE UNIQUE INDEX uq_candidate_active_cv
    ON candidate_cvs (candidate_id)
    WHERE is_active = true;

CREATE INDEX idx_candidate_cvs_candidate
    ON candidate_cvs (candidate_id, uploaded_at DESC);

-- 3. Talent Pool participation audit log
CREATE TABLE candidate_talent_pool_audits (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    candidate_id UUID         NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    event_type   VARCHAR(30)  NOT NULL
                 CHECK (event_type IN ('JOINED_POOL', 'LEFT_POOL', 'UPDATED_CV', 'REMOVED_CV')),
    joined_pool  BOOLEAN      NOT NULL,
    cv_id        UUID         REFERENCES candidate_cvs (id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    occurred_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_talent_pool_audit_candidate
    ON candidate_talent_pool_audits (candidate_id, occurred_at DESC);
