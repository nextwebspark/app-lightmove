-- When the last AI enrichment run produced nothing, so the drawer can say it failed instead of waiting.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN ai_enrich_failed_at timestamptz;
