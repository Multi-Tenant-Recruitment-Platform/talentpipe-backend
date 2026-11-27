-- =============================================================================
-- V30: auto-advanced applicants go straight on to interview.
--
-- From now on screening moves an auto-advanced applicant to the vacancy's
-- first INTERVIEW stage (V29 left them at SCREENING). This brings the ones
-- already screened into line. Only applicants still AUTO_ADVANCED, on a
-- vacancy that has an interview stage, are moved; nothing else changes.
-- =============================================================================

WITH first_interview AS (
    SELECT DISTINCT ON (s.job_id) s.job_id, s.tenant_id, s.id
    FROM job_stages s
    WHERE s.stage_type = 'INTERVIEW'
    ORDER BY s.job_id, s.position
)
UPDATE applications a
SET current_stage_id = fi.id
FROM first_interview fi
WHERE a.job_id = fi.job_id
  AND a.tenant_id = fi.tenant_id
  AND a.review_status = 'AUTO_ADVANCED'
  AND a.current_stage_id IS DISTINCT FROM fi.id;
