-- ---------------------------------------------------------------------------
-- V7: Notification audit trail + invitation tokens (Sprint 1 / Week 2).
--
-- notifications: per-attempt record of outbound email for COMPANY USERS.
--   Tenant-scoped and FK'd to users(id) per the design doc. Candidate emails
--   deliberately do NOT go through this table — candidates have no tenant and
--   are not users rows, so forcing them in would corrupt the tenant scoping.
--   Candidate sends are logged (with correlation id) but not persisted; a
--   dedicated candidate_notifications table is a later decision.
--
-- invitation_tokens: single-use tokens for the invite → accept flow
--   (PB-003/PB-004). Same shape and hashing rules as email_verification_tokens
--   and password_reset_tokens: SHA-256 hash only, used_at marks consumption,
--   expiry (7 days) enforced at the service layer. The invited user's tenant
--   and role live on the INVITED users row itself, so the token table needs
--   no tenant/role columns.
-- ---------------------------------------------------------------------------

CREATE TABLE notifications (
    id          UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type        VARCHAR(30)  NOT NULL
                CHECK (type IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET', 'INVITATION')),
    recipient   VARCHAR(255) NOT NULL,
    status      VARCHAR(10)  NOT NULL DEFAULT 'PENDING'
                CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    -- Truncated failure detail for operations; never contains the link/token.
    error_detail VARCHAR(500),
    sent_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_tenant ON notifications (tenant_id);
CREATE INDEX idx_notifications_user   ON notifications (user_id);
CREATE INDEX idx_notifications_status ON notifications (status);

CREATE TABLE invitation_tokens (
    id          UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invt_user ON invitation_tokens (user_id);
CREATE INDEX idx_invt_hash ON invitation_tokens (token_hash);

-- ---------------------------------------------------------------------------
-- Brute-force protection for candidates.
--
-- users already carries failed_login_count / locked_until (V2). Candidates are
-- a separate identity with their own login path, so they need the same two
-- columns to get the same 5-attempts / 15-minute lockout.
-- ---------------------------------------------------------------------------
ALTER TABLE candidates
    ADD COLUMN failed_login_count SMALLINT     NOT NULL DEFAULT 0,
    ADD COLUMN locked_until       TIMESTAMPTZ;
