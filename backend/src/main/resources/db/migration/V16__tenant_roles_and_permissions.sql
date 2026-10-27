-- ---------------------------------------------------------------------------
-- V16: Company-defined roles built from a fixed permission catalogue.
--
-- Until now a company member held one of three fixed roles (COMPANY_ADMIN,
-- HR_MANAGER, INTERVIEWER) from the global `roles` table. Companies do not
-- all work that way, so each workspace now owns its roles: a name it chooses
-- plus the set of permissions ("tasks") its members may perform. The
-- permissions themselves are defined in code (com.talentpipe.security.Permission)
-- because each one means something only through the endpoint that checks it.
--
-- Every existing workspace gets the three default roles — Owner, Recruiter,
-- Interviewer — and every existing member is moved onto the one matching
-- their old fixed role, so nobody's access changes on deploy.
--
-- Numbering: V14 and V15 are reserved for the pipeline-template and
-- applications branches still in review. A local database that runs V16
-- before those arrive needs a reset (or spring.flyway.out-of-order=true).
-- ---------------------------------------------------------------------------

CREATE TABLE tenant_roles (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id    UUID         NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name         VARCHAR(60)  NOT NULL,
    description  VARCHAR(300),
    -- Which default template the role is; NULL for a role the company created.
    system_key   VARCHAR(20)  CHECK (system_key IN ('OWNER', 'RECRUITER', 'INTERVIEWER')),
    version      INTEGER      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Target of the composite foreign key from users below.
    CONSTRAINT uq_tenant_roles_id_tenant UNIQUE (id, tenant_id)
);

-- Role names are unique within a workspace, ignoring case.
CREATE UNIQUE INDEX uq_tenant_roles_name ON tenant_roles (tenant_id, lower(name));
-- One copy of each default role per workspace.
CREATE UNIQUE INDEX uq_tenant_roles_system ON tenant_roles (tenant_id, system_key)
    WHERE system_key IS NOT NULL;

-- One row per permission a role grants. The Owner role stores none: it always
-- holds every permission, including ones added to the catalogue later.
CREATE TABLE tenant_role_permissions (
    role_id     UUID         NOT NULL REFERENCES tenant_roles (id) ON DELETE CASCADE,
    permission  VARCHAR(60)  NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- ---------------------------------------------------------------------------
-- Users hold a company role.
--
-- The foreign key is composite — (tenant_role_id, tenant_id) — so the database
-- itself guarantees a member can only hold a role of their own company. With
-- the default MATCH SIMPLE it is skipped for platform operators, whose
-- tenant_id and tenant_role_id are both NULL.
--
-- No ON DELETE action: deleting a role that still has members fails, which
-- backs up the application rule that members must be moved first.
--
-- role_id becomes a transitional mirror of the old fixed role: kept for
-- platform operators and for members of the default roles, NULL for members
-- of a custom role. Code still reading it keeps working for the default roles
-- until it moves to permissions.
-- ---------------------------------------------------------------------------
ALTER TABLE users
    ADD COLUMN tenant_role_id UUID,
    ADD CONSTRAINT fk_users_tenant_role
        FOREIGN KEY (tenant_role_id, tenant_id) REFERENCES tenant_roles (id, tenant_id),
    ALTER COLUMN role_id DROP NOT NULL;

CREATE INDEX idx_users_tenant_role ON users (tenant_role_id);

-- ---------------------------------------------------------------------------
-- Backfill: the default roles for every existing workspace…
--
-- The Recruiter and Interviewer permission lists below are the templates as
-- they stand today (SystemRole in code). They are copied, not referenced: each
-- workspace owns its roles and may change them, so later edits to the code
-- template apply only to workspaces created after the edit.
-- ---------------------------------------------------------------------------
INSERT INTO tenant_roles (tenant_id, name, description, system_key)
SELECT t.id, r.name, r.description, r.system_key
FROM tenants t
CROSS JOIN (VALUES
    ('Owner',       'Runs the workspace. Holds every permission, always.',               'OWNER'),
    ('Recruiter',   'Runs hiring day to day: vacancies, applicants and interviews.',    'RECRUITER'),
    ('Interviewer', 'Interviews candidates for the vacancies they are assigned to.',     'INTERVIEWER')
) AS r (name, description, system_key);

INSERT INTO tenant_role_permissions (role_id, permission)
SELECT tr.id, p.permission
FROM tenant_roles tr
JOIN (VALUES
    ('RECRUITER',   'jobs.view.all'),
    ('RECRUITER',   'jobs.manage'),
    ('RECRUITER',   'jobs.publish'),
    ('RECRUITER',   'applications.view.all'),
    ('RECRUITER',   'applications.manage'),
    ('RECRUITER',   'interviews.schedule'),
    ('INTERVIEWER', 'interviews.conduct')
) AS p (system_key, permission) ON p.system_key = tr.system_key;

-- …and every existing member onto the role matching their old fixed role.
UPDATE users u
SET tenant_role_id = tr.id
FROM roles r, tenant_roles tr
WHERE u.role_id = r.id
  AND u.tenant_id IS NOT NULL
  AND tr.tenant_id = u.tenant_id
  AND tr.system_key = CASE r.name
                          WHEN 'COMPANY_ADMIN' THEN 'OWNER'
                          WHEN 'HR_MANAGER'    THEN 'RECRUITER'
                          WHEN 'INTERVIEWER'   THEN 'INTERVIEWER'
                      END;

-- Every company member now has a company role; platform operators have a
-- fixed role and no company role. From here on that is enforced.
ALTER TABLE users
    ADD CONSTRAINT chk_users_role_kind CHECK (
        (tenant_id IS NOT NULL AND tenant_role_id IS NOT NULL)
        OR (tenant_id IS NULL AND tenant_role_id IS NULL AND role_id IS NOT NULL)
    );

-- ---------------------------------------------------------------------------
-- Audit trail of who changed which role, and who moved whom between roles.
--
-- role_id is deliberately not a foreign key: the trail must outlive the roles
-- it describes, so the role's name is recorded alongside it.
-- ---------------------------------------------------------------------------
CREATE TABLE tenant_role_audit_events (
    id              UUID          PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id       UUID          NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    actor_user_id   UUID          REFERENCES users (id) ON DELETE SET NULL,
    action          VARCHAR(30)   NOT NULL
                    CHECK (action IN ('ROLE_CREATED', 'ROLE_UPDATED', 'ROLE_DELETED', 'MEMBER_ROLE_CHANGED')),
    role_id         UUID,
    role_name       VARCHAR(60)   NOT NULL,
    target_user_id  UUID          REFERENCES users (id) ON DELETE SET NULL,
    previous_value  VARCHAR(2000),
    new_value       VARCHAR(2000),
    occurred_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_role_audit_tenant ON tenant_role_audit_events (tenant_id, occurred_at DESC);
