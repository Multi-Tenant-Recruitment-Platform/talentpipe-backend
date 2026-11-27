-- ---------------------------------------------------------------------------
-- V28: Ideal candidate matching weighs skills only.
--
-- The 100% is now shared by the vacancy's skills alone — required skills as
-- must-haves, preferred skills as preferred. Tools and technologies and
-- relevant experience join education: read from the vacancy's candidate
-- requirements and passed to screening as context, never weighted.
--
--   1. Rescale    each vacancy's remaining skill weights back up to the total
--                 the profile had, so a complete (100%) profile stays complete
--                 and a live vacancy stays publishable
--   2. Switch off matching on a vacancy that weighed only tools or experience,
--                 since it is left with nothing to weigh
--   3. Remove     the tool and experience rows
--   4. Tighten    the constraints to the one kind of criterion left
-- ---------------------------------------------------------------------------

-- ------------------------------------------------------------- 1. Rescale
--
-- Whole percents, as the job form and the service read weights. The target is
-- the total the profile had before (capped at 100%: a draft may have been over,
-- and one weight can never pass 100%). Largest remainder keeps the result whole
-- and exact: shares are floored, and the leftover points go to the largest
-- fractions, ties broken by the recruiter's order. Scaling only ever goes up,
-- so no weight falls below the 1% it had.
WITH weighted AS (
    SELECT c.id,
           c.job_id,
           c.position,
           c.name,
           c.category,
           ROUND(c.weight * 100) AS pct
    FROM job_screening_criteria c
    WHERE c.weight IS NOT NULL
),
totals AS (
    SELECT job_id,
           LEAST(SUM(pct), 100)                                      AS target,
           SUM(pct) FILTER (WHERE category = 'TECHNICAL_SKILL')     AS kept
    FROM weighted
    GROUP BY job_id
),
exact AS (
    SELECT w.id,
           w.job_id,
           w.position,
           w.name,
           t.target,
           w.pct * t.target / t.kept AS share
    FROM weighted w
    JOIN totals t ON t.job_id = w.job_id
    WHERE w.category = 'TECHNICAL_SKILL'
      AND t.kept > 0
      AND t.kept <> t.target
),
floored AS (
    SELECT id,
           FLOOR(share)                                                          AS base,
           target - SUM(FLOOR(share)) OVER (PARTITION BY job_id)                 AS spare,
           ROW_NUMBER() OVER (PARTITION BY job_id
                              ORDER BY share - FLOOR(share) DESC, position, name) AS place
    FROM exact
)
UPDATE job_screening_criteria c
SET weight = (f.base + CASE WHEN f.place <= f.spare THEN 1 ELSE 0 END) / 100.0
FROM floored f
WHERE c.id = f.id;

-- ---------------------------------------------------------- 2. Switch off
--
-- A profile that weighed only tools or experience has nothing left to score
-- against. Matching goes off, so screening uses its general criteria and the
-- vacancy — live or not — is not left in a state it could not be saved in.
-- Decided before the rows go, so a draft that simply has no rows yet keeps its
-- switch as the recruiter left it.
UPDATE job_vacancies v
SET ideal_matching_enabled = false
WHERE v.ideal_matching_enabled
  AND EXISTS (SELECT 1 FROM job_screening_criteria c
              WHERE c.job_id = v.id AND c.category <> 'TECHNICAL_SKILL')
  AND NOT EXISTS (SELECT 1 FROM job_screening_criteria c
                  WHERE c.job_id = v.id AND c.category = 'TECHNICAL_SKILL');

-- -------------------------------------------------------------- 3. Remove
DELETE FROM job_screening_criteria
WHERE category <> 'TECHNICAL_SKILL';

-- ------------------------------------------------------------- 4. Tighten
--
-- Every criterion is now a skill, and every skill says must-have or preferred.
ALTER TABLE job_screening_criteria DROP CONSTRAINT chk_criteria_requirement_kind;
ALTER TABLE job_screening_criteria DROP CONSTRAINT chk_criteria_category;
ALTER TABLE job_screening_criteria
    ADD CONSTRAINT chk_criteria_category CHECK (category = 'TECHNICAL_SKILL');
ALTER TABLE job_screening_criteria ALTER COLUMN requirement SET NOT NULL;

-- There is no experience row to keep single any more.
DROP INDEX uq_criteria_job_experience;
