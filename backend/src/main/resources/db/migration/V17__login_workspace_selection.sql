-- ---------------------------------------------------------------------------
-- V17: Choosing a workspace at sign-in.
--
-- The sign-in modal sends only an email and a password. When those match
-- more than one account (the same person in two companies, or a company
-- account and a candidate account), the API answers 409
-- WORKSPACE_SELECTION_REQUIRED and the person picks one. This table holds
-- that pending choice until it is made: a single-use token valid for two
-- minutes, of which only the SHA-256 hash is stored, and the accounts it may
-- be exchanged for.
--
-- account_id is deliberately not a foreign key. It is either a users.id or a
-- candidates.id, the same arrangement as refresh_tokens (V6).
-- ---------------------------------------------------------------------------
CREATE TABLE login_selections (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Expired rows are purged whenever a new choice is recorded.
CREATE INDEX idx_login_selections_expires ON login_selections (expires_at);

CREATE TABLE login_selection_accounts (
    selection_id UUID NOT NULL REFERENCES login_selections (id) ON DELETE CASCADE,
    account_id   UUID NOT NULL,
    PRIMARY KEY (selection_id, account_id)
);
