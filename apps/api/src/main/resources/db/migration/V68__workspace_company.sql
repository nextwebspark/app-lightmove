-- Signup's organisation step picks the firm from the company universe, the way a client is picked.
--
-- A write-time snapshot on V48's terms: the apollo id records which row was resolved, and the rest is
-- copied from it once and owned by the workspace afterwards. No foreign key — the pipeline reloads
-- app_lm_apollo_companies wholesale. A firm the universe does not carry leaves all of it null.
ALTER TABLE app_lm_workspace
    ADD COLUMN apollo_account_id    text,
    ADD COLUMN company_industry     text,
    ADD COLUMN company_city         text,
    ADD COLUMN company_country      text,
    ADD COLUMN company_website      text,
    ADD COLUMN company_linkedin_url text,
    ADD COLUMN logo_url             text;
