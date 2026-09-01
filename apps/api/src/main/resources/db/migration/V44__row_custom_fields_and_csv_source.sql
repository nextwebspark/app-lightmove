-- Where a custom column's values actually live, and the door a spreadsheet comes through.
--
-- V43 holds the definitions; this holds the values. One jsonb bag per row, keyed by the field_key of
-- a column the project has defined. The same argument V30 makes for the strategy filter and V36 for a
-- candidate's career history: read whole, written whole, never queried by axis. Nothing will ever ask
-- "which candidates have an ethnicity of X" across mandates — the keys are not even the same set from
-- one mandate to the next — so a bag is the honest shape and a column per key is not available.
--
-- Deliberately a new column rather than folding into app_lm_project_candidate.profile. That one is a
-- typed record the application reads by field (career, languages); this one is an open map whose keys
-- belong to the project. Sharing them would stop CandidateProfile round-tripping, because it would
-- have to preserve keys it knows nothing about.

ALTER TABLE app_lm_project_triage_company
    ADD COLUMN custom_fields jsonb NOT NULL DEFAULT '{}'::jsonb;

ALTER TABLE app_lm_project_candidate
    ADD COLUMN custom_fields jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN app_lm_project_triage_company.custom_fields IS
    'Values for this project''s COMPANY custom columns, keyed by app_lm_project_custom_column.field_key.';

COMMENT ON COLUMN app_lm_project_candidate.custom_fields IS
    'Values for this project''s CANDIDATE custom columns, keyed by app_lm_project_custom_column.field_key.';

-- V34 listed the three doors a company could arrive through and a spreadsheet was not one of them.
-- The candidate side already allows 'CSV' (V36 wrote it in from the start, for the import that did not
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
