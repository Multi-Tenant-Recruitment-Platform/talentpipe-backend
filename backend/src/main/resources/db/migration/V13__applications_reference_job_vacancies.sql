-- ---------------------------------------------------------------------------
-- V13: Applications belong to the current job-vacancy model.
--
-- V11 created applications against the original jobs table. The public board
-- and recruiter API now use job_vacancies, so submissions must reference the
-- vacancy id shown to candidates.
-- ---------------------------------------------------------------------------

ALTER TABLE applications
    DROP CONSTRAINT IF EXISTS applications_job_id_fkey;

ALTER TABLE applications
    ADD CONSTRAINT applications_job_id_fkey
    FOREIGN KEY (job_id) REFERENCES job_vacancies (id) ON DELETE CASCADE;
