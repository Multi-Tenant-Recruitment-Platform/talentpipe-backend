-- ---------------------------------------------------------------------------
-- V20: Feature switches, saved on the server.
--
-- The dashboard has switches that used to live only in the browser. They are
-- stored here so they follow the company, not one person's browser:
--
--   * per vacancy (PB-015): AI screening of applications, and matching the
--     talent pool against the vacancy;
--   * per company: blind screening (hide who applicants are while their
--     applications are reviewed), and the AI screening / candidate matching
--     a new vacancy starts with.
--
-- Everything starts off. Turning a company default on changes only vacancies
-- created afterwards; existing ones keep their own setting.
-- ---------------------------------------------------------------------------

ALTER TABLE job_vacancies
    ADD COLUMN ai_screening_enabled       BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN candidate_matching_enabled BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE tenants
    ADD COLUMN blind_screening_enabled    BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN ai_screening_default       BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN candidate_matching_default BOOLEAN NOT NULL DEFAULT false;
