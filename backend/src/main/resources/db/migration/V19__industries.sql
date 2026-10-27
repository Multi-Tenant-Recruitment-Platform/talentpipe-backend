-- ---------------------------------------------------------------------------
-- V19: Industries for companies and their vacancies.
--
-- A company works in one main industry (tenants.industry, since V2) and up to
-- three more (additional_industries, new). Both now take values from a fixed
-- list, ProfileTaxonomy.INDUSTRIES, stored as the labels the forms have
-- always sent ("Finance & Banking"). Each vacancy is filed under one of its
-- company's industries (job_vacancies.industry, new); the public board can be
-- filtered by it.
-- ---------------------------------------------------------------------------

ALTER TABLE tenants
    ADD COLUMN additional_industries TEXT[] NOT NULL DEFAULT '{}';

-- ---------------------------------------------------------------------------
-- Existing company industries were free text. Bring each to its canonical
-- label using the application's matching rule (OptionKey: lower case, "&" read
-- as "and", everything but letters and digits dropped), so "finance and
-- banking" becomes "Finance & Banking".
--
-- A value that matches no listed industry becomes 'Other'. The forms only ever
-- offered the list, so such values can only have arrived through the API
-- directly; keeping them would make the company's next profile save fail
-- validation on a field it did not touch.
-- ---------------------------------------------------------------------------
UPDATE tenants SET industry = NULL WHERE btrim(industry) = '';

UPDATE tenants t
SET industry = coalesce(
        (SELECT c.label
         FROM (VALUES ('Information Technology'),
                      ('Finance & Banking'),
                      ('Healthcare'),
                      ('Education'),
                      ('Retail & E-commerce'),
                      ('Manufacturing'),
                      ('Hospitality & Leisure'),
                      ('Construction & Engineering'),
                      ('Logistics & Transport'),
                      ('Other')) AS c (label)
         WHERE regexp_replace(replace(lower(c.label), '&', ' and '), '[^a-z0-9]', '', 'g')
             = regexp_replace(replace(lower(t.industry), '&', ' and '), '[^a-z0-9]', '', 'g')),
        'Other')
WHERE t.industry IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Vacancies. Existing ones are filed under their company's main industry,
-- which is also what a new vacancy defaults to.
-- ---------------------------------------------------------------------------
ALTER TABLE job_vacancies
    ADD COLUMN industry VARCHAR(100);

UPDATE job_vacancies v
SET industry = t.industry
FROM tenants t
WHERE t.id = v.tenant_id
  AND t.industry IS NOT NULL;

-- Board filter by industry: equality on the canonical label, then the board's
-- newest-first order, so one industry's adverts are read off the index
-- already sorted. Partial on PUBLISHED like the other board indexes (V12).
CREATE INDEX idx_job_vacancies_public_industry
    ON job_vacancies (industry, published_at DESC, id)
    WHERE status = 'PUBLISHED';
