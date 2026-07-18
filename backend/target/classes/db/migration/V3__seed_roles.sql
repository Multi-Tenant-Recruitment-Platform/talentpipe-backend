-- ---------------------------------------------------------------------------
-- V3: Seed the fixed role vocabulary.
--
-- Roles are reference data owned by migrations, not by application code.
-- ---------------------------------------------------------------------------
INSERT INTO roles (name, description)
VALUES ('SUPER_ADMIN',   'Platform operator; not bound to any tenant'),
       ('COMPANY_ADMIN', 'Tenant owner; manages company settings and staff'),
       ('HR_MANAGER',    'Manages jobs, candidates and pipelines within a tenant'),
       ('INTERVIEWER',   'Conducts interviews and submits feedback within a tenant'),
       ('CANDIDATE',     'Job applicant (candidate-side features arrive in a later sprint)');
