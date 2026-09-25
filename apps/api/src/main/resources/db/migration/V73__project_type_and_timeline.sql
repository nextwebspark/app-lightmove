-- The New position modal's two decisions: what the mandate delivers (a mapped universe, or a full
-- search through to a shortlist) and when. `target_date` is untouched: it stays the brief's hire
-- "Target start", which is a different date from when the client expects the work back.
-- Existing rows read as SEARCH with no timeline, and the list falls back to `target_date`.
ALTER TABLE app_lm_project
    ADD COLUMN project_type varchar(16) NOT NULL DEFAULT 'SEARCH',
    ADD COLUMN start_date date,
    ADD COLUMN delivery_date date,
    ADD COLUMN mapping_target_date date,
    ADD CONSTRAINT app_lm_project_type_chk CHECK (project_type IN ('MAPPING', 'SEARCH')),
    ADD CONSTRAINT app_lm_project_delivery_after_start_chk
        CHECK (start_date IS NULL OR delivery_date IS NULL OR delivery_date > start_date),
    ADD CONSTRAINT app_lm_project_mapping_target_on_search_chk
        CHECK (mapping_target_date IS NULL OR project_type = 'SEARCH');
