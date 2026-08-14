-- ---------------------------------------------------------------------------
-- V8: Company profile columns on the tenants table.
--
-- All columns are nullable — existing tenants are not broken; the profile
-- is filled in progressively through the Dashboard Settings page.
--
-- Design: profile data lives directly on tenants (no separate 1:1 table)
-- because the root entity has few rows, the join would add zero benefit,
-- and the frontend always fetches both together.
--
-- TEXT[] (Postgres native arrays) is used for list fields (values, benefits,
-- work_modes, …). Hibernate 6.x maps these via @JdbcTypeCode(SqlTypes.ARRAY).
-- ---------------------------------------------------------------------------

ALTER TABLE tenants
    -- Branding
    ADD COLUMN logo_url           TEXT,
    ADD COLUMN cover_image_url    TEXT,
    ADD COLUMN tagline            VARCHAR(140),

    -- Company identity
    ADD COLUMN company_type       VARCHAR(100),
    ADD COLUMN size               VARCHAR(100),
    ADD COLUMN employee_count     INTEGER,
    ADD COLUMN founded_year       INTEGER,
    ADD COLUMN description        TEXT,
    ADD COLUMN culture            TEXT,
    ADD COLUMN mission            TEXT,
    ADD COLUMN vision             TEXT,
    ADD COLUMN legal_name         VARCHAR(255),
    ADD COLUMN registration_number VARCHAR(60),

    -- Localisation
    ADD COLUMN timezone           VARCHAR(100),
    ADD COLUMN currency           VARCHAR(10),
    ADD COLUMN language           VARCHAR(10),

    -- Contact
    ADD COLUMN email              VARCHAR(255),
    ADD COLUMN hr_email           VARCHAR(255),
    ADD COLUMN phone              VARCHAR(50),
    ADD COLUMN alternative_phone  VARCHAR(50),
    ADD COLUMN website            TEXT,
    ADD COLUMN linkedin_url       TEXT,
    ADD COLUMN facebook_url       TEXT,
    ADD COLUMN twitter_url        TEXT,
    ADD COLUMN instagram_url      TEXT,

    -- Location
    ADD COLUMN address            VARCHAR(255),
    ADD COLUMN city               VARCHAR(120),
    ADD COLUMN state              VARCHAR(120),
    ADD COLUMN postal_code        VARCHAR(60),
    ADD COLUMN country            VARCHAR(120),

    -- List / taxonomy fields (Postgres TEXT arrays; default empty array)
    ADD COLUMN values             TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN benefits           TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN work_modes         TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN office_locations   TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN departments        TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN teams              TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN business_units     TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN employment_types   TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN job_categories     TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN job_families       TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN job_levels         TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN job_titles         TEXT[] NOT NULL DEFAULT '{}';

-- Index on country + city for the public company directory (future).
CREATE INDEX idx_tenants_country ON tenants (country) WHERE country IS NOT NULL;
