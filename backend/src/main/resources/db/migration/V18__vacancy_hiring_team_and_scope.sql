-- ---------------------------------------------------------------------------
-- V18: Who works on a vacancy.
--
-- Not everyone in a company should see every vacancy. A member whose role
-- lacks jobs.view.all now sees only the vacancies they work on:
--
--   * the ones they created              (created_by, new here)
--   * the ones they recruit for          (assigned_recruiter_id, since V12)
--   * the ones they are hiring manager of (hiring_manager_id, since V12)
--   * the ones they are on the hiring team of (job_vacancy_team_members, new)
--
-- The hiring team is everyone else involved: interviewers, a second
-- recruiter, someone from the business who reviews applicants. What each of
-- them may DO is still decided by their company role; being on the team only
-- decides what they can SEE.
-- ---------------------------------------------------------------------------

-- Unknown for vacancies created before this migration. SET NULL for the same
-- reason as the V12 assignment columns: losing a person must never take the
-- vacancies they created with them.
ALTER TABLE job_vacancies
    ADD COLUMN created_by UUID REFERENCES users (id) ON DELETE SET NULL;

-- Targets for the composite foreign keys below, which keep a team member in
-- the same company as the vacancy they are on, whatever writes the row.
ALTER TABLE job_vacancies
    ADD CONSTRAINT uq_job_vacancies_id_tenant UNIQUE (id, tenant_id);
ALTER TABLE users
    ADD CONSTRAINT uq_users_id_tenant UNIQUE (id, tenant_id);

CREATE TABLE job_vacancy_team_members (
    vacancy_id UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    tenant_id  UUID        NOT NULL,
    added_by   UUID        REFERENCES users (id) ON DELETE SET NULL,
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (vacancy_id, user_id),
    CONSTRAINT fk_vacancy_team_vacancy FOREIGN KEY (vacancy_id, tenant_id)
        REFERENCES job_vacancies (id, tenant_id) ON DELETE CASCADE,
    -- Removing a person from the company removes them from its hiring teams.
    CONSTRAINT fk_vacancy_team_user FOREIGN KEY (user_id, tenant_id)
        REFERENCES users (id, tenant_id) ON DELETE CASCADE
);

-- "Which vacancies is this person on?" — the scoped dashboard list.
CREATE INDEX idx_vacancy_team_user ON job_vacancy_team_members (user_id);

-- The other three ways onto a vacancy, for the same question.
CREATE INDEX idx_job_vacancies_tenant_creator
    ON job_vacancies (tenant_id, created_by) WHERE created_by IS NOT NULL;
CREATE INDEX idx_job_vacancies_tenant_recruiter
    ON job_vacancies (tenant_id, assigned_recruiter_id) WHERE assigned_recruiter_id IS NOT NULL;
CREATE INDEX idx_job_vacancies_tenant_manager
    ON job_vacancies (tenant_id, hiring_manager_id) WHERE hiring_manager_id IS NOT NULL;
