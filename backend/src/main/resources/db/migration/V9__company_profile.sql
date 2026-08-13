-- ---------------------------------------------------------------------------
-- V9: Company profile (PB-005).
--
-- Numbered 9, not 8: the shared development database already carries a
-- `V8__candidate_google_sign_in` applied from another branch, so version 8 is
-- spoken for even though its file is not on `development` yet. Taking the next
-- free number is what keeps both migrations applicable in either order.
--
-- Widens `tenants` from bare identity (name, subdomain, industry) into the
-- full company profile the settings screen edits. Every column added here is
-- NULLABLE with no default: an existing workspace is a valid, if empty,
-- profile, so this migration cannot fail on live data and needs no backfill.
--
-- Two shapes are used deliberately:
--
--   * scalars    -> typed columns. They are individually queryable and will be
--                   filtered on (industry, country, company_size) once the
--                   public company directory exists.
--
--   * list fields -> JSONB. These are free-form vocabularies the admin curates
--                   (departments, job levels, benefits …). Their membership
--                   changes as a set, they are always read whole, and none of
--                   them is referenced by another row today. Child tables would
--                   be twelve joins for data that is never queried
--                   independently; JSONB keeps it one row and one write, and
--                   can still be indexed with GIN if that changes.
--
-- Images are stored inline as BYTEA rather than on a filesystem or in object
-- storage: the platform has neither configured, and a logo is small and
-- inherently tenant-scoped. Keeping the bytes in the tenant row means an image
-- cannot outlive its tenant or leak across one, and it adds no new infra to
-- the Docker stack. Revisit if covers grow or a CDN appears.
-- ---------------------------------------------------------------------------

ALTER TABLE tenants
    -- Identity and positioning ------------------------------------------------
    ADD COLUMN tagline             VARCHAR(255),
    ADD COLUMN company_type        VARCHAR(100),
    ADD COLUMN company_size        VARCHAR(50),
    ADD COLUMN employee_count      INTEGER,
    ADD COLUMN founded_year        INTEGER,
    ADD COLUMN legal_name          VARCHAR(255),
    ADD COLUMN registration_number VARCHAR(100),

    -- Narrative --------------------------------------------------------------
    ADD COLUMN description         TEXT,
    ADD COLUMN culture             TEXT,
    ADD COLUMN mission             TEXT,
    ADD COLUMN vision              TEXT,

    -- Operating defaults -----------------------------------------------------
    ADD COLUMN timezone            VARCHAR(64),
    ADD COLUMN currency            VARCHAR(10),
    ADD COLUMN language            VARCHAR(50),

    -- Contact ----------------------------------------------------------------
    ADD COLUMN email               VARCHAR(255),
    ADD COLUMN hr_email            VARCHAR(255),
    ADD COLUMN phone               VARCHAR(50),
    ADD COLUMN alternative_phone   VARCHAR(50),
    ADD COLUMN website             VARCHAR(255),
    ADD COLUMN linkedin_url        VARCHAR(255),
    ADD COLUMN facebook_url        VARCHAR(255),
    ADD COLUMN twitter_url         VARCHAR(255),
    ADD COLUMN instagram_url       VARCHAR(255),

    -- Location ---------------------------------------------------------------
    ADD COLUMN address             VARCHAR(500),
    ADD COLUMN city                VARCHAR(100),
    ADD COLUMN state               VARCHAR(100),
    ADD COLUMN postal_code         VARCHAR(20),
    ADD COLUMN country             VARCHAR(100),

    -- Curated vocabularies. '[]' rather than NULL so readers never branch on
    -- "absent vs empty" — an unset list and an emptied one mean the same thing.
    ADD COLUMN core_values         JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN benefits            JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN work_modes          JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN office_locations    JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN departments         JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN teams               JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN business_units      JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN employment_types    JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN job_categories      JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN job_families        JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN job_levels          JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN job_titles          JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Branding. Content type is stored with the bytes so the serving endpoint
    -- echoes what was uploaded instead of guessing from a sniff.
    ADD COLUMN logo_image          BYTEA,
    ADD COLUMN logo_content_type   VARCHAR(100),
    ADD COLUMN cover_image         BYTEA,
    ADD COLUMN cover_content_type  VARCHAR(100);

-- `values` is a reserved word in SQL, hence core_values above; the API still
-- calls it `values`, which is the word the product uses.
COMMENT ON COLUMN tenants.core_values IS 'API name: values. Renamed because VALUES is reserved SQL.';

-- Bytes and their content type are meaningless apart, so keep them consistent
-- at the database level rather than trusting every future write path.
ALTER TABLE tenants
    ADD CONSTRAINT tenants_logo_complete
        CHECK ((logo_image IS NULL) = (logo_content_type IS NULL)),
    ADD CONSTRAINT tenants_cover_complete
        CHECK ((cover_image IS NULL) = (cover_content_type IS NULL));
