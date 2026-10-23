-- ---------------------------------------------------------------------------
-- V21: Room for the currency the company profile form sends.
--
-- The form offers currencies as labels ("LKR — Sri Lankan rupee", as the
-- vacancy form does), but tenants.currency held only 10 characters, so saving
-- a profile with a currency chosen failed validation. Same width as
-- job_vacancies.currency (V12).
-- ---------------------------------------------------------------------------
ALTER TABLE tenants
    ALTER COLUMN currency TYPE VARCHAR(64);
