-- ---------------------------------------------------------------------------
-- V10: Drop unused company profile columns (post-sprint client feedback).
--
-- These 12 fields were removed from the Dashboard Settings UI after the
-- sprint review.  They are no longer referenced by the backend DTO,
-- service, or mapper layers.
--
-- Using DROP COLUMN IF EXISTS so this migration is idempotent against
-- partially-migrated dev databases.
-- ---------------------------------------------------------------------------

ALTER TABLE tenants
    DROP COLUMN IF EXISTS culture,
    DROP COLUMN IF EXISTS "values",
    DROP COLUMN IF EXISTS work_modes,
    DROP COLUMN IF EXISTS address,
    DROP COLUMN IF EXISTS employee_count,
    DROP COLUMN IF EXISTS teams,
    DROP COLUMN IF EXISTS business_units,
    DROP COLUMN IF EXISTS employment_types,
    DROP COLUMN IF EXISTS job_levels,
    DROP COLUMN IF EXISTS job_categories,
    DROP COLUMN IF EXISTS job_families,
    DROP COLUMN IF EXISTS job_titles;
