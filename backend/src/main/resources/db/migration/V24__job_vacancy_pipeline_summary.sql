-- ---------------------------------------------------------------------------
-- V24: A vacancy remembers what its pipeline looked like when it was created.
--
-- The recruiter dashboard shows, for every vacancy, the template it was
-- created from and the shape of its pipeline. All three values are frozen
-- when the vacancy is created, like the stages themselves (V23):
--
--   pipeline_template_name  the template's name at that moment. Not read
--                           live: a template can be renamed, and deleting it
--                           clears pipeline_template_id (SET NULL, V23), yet
--                           the vacancy still runs on the pipeline it copied.
--   stage_count /           counted from the copied stages. job_stages rows
--   interview_round_count   are written once and never changed, so the counts
--                           cannot drift from them — and storing them spares
--                           the paged vacancy list a count per row.
--
-- Vacancies created before V23 have no pipeline and keep NULL in all three.
-- ---------------------------------------------------------------------------

ALTER TABLE job_vacancies
    ADD COLUMN pipeline_template_name VARCHAR(120),
    ADD COLUMN stage_count            INTEGER
        CONSTRAINT chk_job_vacancies_stage_count CHECK (stage_count >= 0),
    ADD COLUMN interview_round_count  INTEGER
        CONSTRAINT chk_job_vacancies_interview_round_count CHECK (interview_round_count >= 0);

-- Backfill the vacancies created between V23 and this migration. The template
-- is read once, here; from now on the name is written only when a vacancy is
-- created.
UPDATE job_vacancies v
SET pipeline_template_name = t.name
FROM pipeline_templates t
WHERE t.id = v.pipeline_template_id;

UPDATE job_vacancies v
SET stage_count           = s.total,
    interview_round_count = s.interviews
FROM (
    SELECT job_id,
           COUNT(*)                                     AS total,
           COUNT(*) FILTER (WHERE stage_type = 'INTERVIEW') AS interviews
    FROM job_stages
    GROUP BY job_id
) s
WHERE s.job_id = v.id;
