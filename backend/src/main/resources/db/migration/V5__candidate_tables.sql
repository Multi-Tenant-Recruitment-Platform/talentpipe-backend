-- ---------------------------------------------------------------------------
-- V5: Candidate registration tables (Sprint 1 / PB-006 & PB-007).
--
-- Candidates are tenant-independent and stored in their own table, separated
-- from the users table.
-- ---------------------------------------------------------------------------

CREATE TABLE candidates (
    id                      UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    email                   VARCHAR(255) NOT NULL UNIQUE,
    password_hash           VARCHAR(255) NOT NULL,
    full_name               VARCHAR(200) NOT NULL,
    identity_card_number    VARCHAR(30)  NOT NULL,
    address                 VARCHAR(500) NOT NULL,
    contact_number          VARCHAR(20)  NOT NULL,
    status                  VARCHAR(20)  NOT NULL DEFAULT 'PENDING_VERIFICATION'
                            CHECK (status IN ('ACTIVE', 'PENDING_VERIFICATION', 'DISABLED')),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Separate token tables to maintain target foreign keys and modularity.
CREATE TABLE candidate_verification_tokens (
    id            UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    candidate_id  UUID        NOT NULL REFERENCES candidates (id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    used_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_cvt_candidate ON candidate_verification_tokens (candidate_id);
CREATE INDEX idx_cvt_hash      ON candidate_verification_tokens (token_hash);
