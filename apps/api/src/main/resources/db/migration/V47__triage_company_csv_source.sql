-- The fourth door a company can arrive through.
--
-- V34 listed the three doors a company could come in by and a spreadsheet was not one of them. The
-- candidate side already allows 'CSV' (V36 wrote it in from the start, for the import that did not
-- exist yet); this brings the company side into line rather than inventing a second spelling for the
-- same door. STRATEGY still has to carry an apollo_account_id — an imported company has none, and the
-- partial unique index on lower(company_name) WHERE apollo_account_id IS NULL already guards it
-- against the hand-typed rows it now sits beside.

ALTER TABLE app_lm_project_triage_company
    DROP CONSTRAINT app_lm_project_triage_company_source_chk;

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_source_chk
        CHECK (source IN ('STRATEGY', 'MANUAL', 'EXTENSION', 'CSV'));

COMMENT ON COLUMN app_lm_project_triage_company.source IS
    'Which door the company came through: STRATEGY (Apollo universe), MANUAL (typed in), EXTENSION (browser plugin), CSV (spreadsheet import).';
