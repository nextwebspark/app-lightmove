-- Where a custom column's values actually live.
--
-- V45 holds the definitions; this holds the values. One jsonb bag per row, keyed by the field_key of
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
