-- ---------------------------------------------------------------------------
-- V10: Recruitment Team Management tables
--
-- Adds recruitment_teams, recruitment_team_members, and recruitment_team_vacancies
-- for managing tenant-scoped recruitment teams, member roles, and vacancy/department mappings.
-- ---------------------------------------------------------------------------

-- Recruitment teams table
CREATE TABLE recruitment_teams (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    department  VARCHAR(100),
    lead_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT  unique_team_name_per_tenant UNIQUE (tenant_id, name)
);

CREATE INDEX idx_recruitment_teams_tenant ON recruitment_teams (tenant_id);
CREATE INDEX idx_recruitment_teams_tenant_dept ON recruitment_teams (tenant_id, department);

-- Recruitment team members junction table
CREATE TABLE recruitment_team_members (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    team_id     UUID NOT NULL REFERENCES recruitment_teams (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    member_role VARCHAR(50) NOT NULL DEFAULT 'RECRUITER'
                CHECK (member_role IN ('LEAD', 'RECRUITER', 'INTERVIEWER', 'HIRING_MANAGER', 'COORDINATOR')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT  unique_team_member UNIQUE (team_id, user_id)
);

CREATE INDEX idx_recruitment_team_members_tenant_team ON recruitment_team_members (tenant_id, team_id);
CREATE INDEX idx_recruitment_team_members_tenant_user ON recruitment_team_members (tenant_id, user_id);

-- Recruitment team vacancies mapping table
CREATE TABLE recruitment_team_vacancies (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    tenant_id   UUID NOT NULL REFERENCES tenants (id) ON DELETE CASCADE,
    team_id     UUID NOT NULL REFERENCES recruitment_teams (id) ON DELETE CASCADE,
    vacancy_id  UUID NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT  unique_team_vacancy UNIQUE (team_id, vacancy_id)
);

CREATE INDEX idx_recruitment_team_vacancies_tenant_team ON recruitment_team_vacancies (tenant_id, team_id);
CREATE INDEX idx_recruitment_team_vacancies_tenant_vacancy ON recruitment_team_vacancies (tenant_id, vacancy_id);
