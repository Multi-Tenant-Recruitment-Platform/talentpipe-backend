-- ---------------------------------------------------------------------------
-- V4: Email verification and password reset tokens (Sprint 1 / Week 2 scope).
--
-- Also widens users.status CHECK to include 'PENDING_VERIFICATION', which is
-- the state a newly-registered account sits in until the user clicks the
-- email link. The lockout logic (failed_login_count / locked_until) deferred
-- from Week 1 is behaviorally activated in AuthService this same sprint.
-- ---------------------------------------------------------------------------

-- Widen the users.status CHECK constraint to include PENDING_VERIFICATION.
-- We drop the old constraint and recreate it with the expanded value set.
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_status_check;

ALTER TABLE users
    ADD CONSTRAINT users_status_check
        CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED', 'PENDING_VERIFICATION'));

-- ---------------------------------------------------------------------------
-- Email verification tokens.
--
-- One row per outstanding token. Only the SHA-256 hash is stored (same
-- rationale as refresh_tokens). Tokens are single-use: used_at marks
-- consumption; expired tokens are also rejected by the service layer.
-- ---------------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_evt_user   ON email_verification_tokens (user_id);
CREATE INDEX idx_evt_hash   ON email_verification_tokens (token_hash);

-- ---------------------------------------------------------------------------
-- Password reset tokens.
--
-- Same shape as email_verification_tokens. TTL enforced at the service layer
-- (30 minutes). After a successful reset all refresh_tokens for the user are
-- revoked so every existing session is invalidated.
-- ---------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prt_user ON password_reset_tokens (user_id);
CREATE INDEX idx_prt_hash ON password_reset_tokens (token_hash);
