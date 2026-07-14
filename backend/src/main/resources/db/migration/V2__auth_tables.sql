-- ---------------------------------------------------------------------------
-- V2: Authentication & tenancy tables (Sprint 1 / Week 1 scope only).
--
-- Only the auth-relevant slice of the schema lands now; jobs, candidates,
-- pipelines etc. arrive in later sprints. Columns match the architecture doc
-- exactly — including failed_login_count / locked_until, which are created
-- now even though the lockout logic itself is Week 2 work, to avoid a schema
-- migration next week (see docs/DECISIONS.md).
-- ---------------------------------------------------------------------------

-- Tenants: one row per customer company. Root of all tenant-scoped data.
CREATE TABLE tenants (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name        VARCHAR(255) NOT NULL,
    subdomain   VARCHAR(100) NOT NULL UNIQUE,
    industry    VARCHAR(100),
    plan_tier   VARCHAR(30)  NOT NULL DEFAULT 'STANDARD'
                CHECK (plan_tier IN ('TRIAL', 'STANDARD', 'ENTERPRISE')),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELLED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Roles: small fixed vocabulary, seeded by V3. Referenced by users.role_id.
CREATE TABLE roles (
    id          SMALLSERIAL PRIMARY KEY,
    name        VARCHAR(30) NOT NULL UNIQUE
                CHECK (name IN ('SUPER_ADMIN', 'COMPANY_ADMIN', 'HR_MANAGER',
                                'INTERVIEWER', 'CANDIDATE')),
    description VARCHAR(255)
);

-- Users: platform staff accounts, scoped to a tenant.
-- tenant_id is NULLABLE on purpose: SUPER_ADMIN operators belong to the
-- platform itself, not to any tenant. Email is unique PER TENANT, not
-- globally — the same person may hold accounts in two companies.
CREATE TABLE users (
    id                 UUID PRIMARY KEY,
    tenant_id          UUID REFERENCES tenants (id) ON DELETE CASCADE,
    role_id            SMALLINT     NOT NULL REFERENCES roles (id),
    email              VARCHAR(255) NOT NULL,
    password_hash      VARCHAR(255) NOT NULL,
    first_name         VARCHAR(100) NOT NULL,
    last_name          VARCHAR(100) NOT NULL,
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                       CHECK (status IN ('ACTIVE', 'INVITED', 'DISABLED')),
    -- Present now, enforced in Week 2 (account lockout / brute-force handling).
    failed_login_count SMALLINT     NOT NULL DEFAULT 0,
    locked_until       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);

CREATE INDEX idx_users_tenant ON users (tenant_id);

-- Refresh tokens: stored HASHED (SHA-256) — a database leak must never yield
-- usable tokens. Rotation on every refresh; revoked_at marks dead tokens.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(255) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Login/refresh both look tokens up by hash.
CREATE INDEX idx_refresh_tokens_token_hash ON refresh_tokens (token_hash);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
