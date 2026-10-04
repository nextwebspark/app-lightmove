-- The model's reading of a candidate against the mandate's competencies: a summary, a 1-10 score with
-- positives and negatives per panel, and the web pages it relied on. Replaced whole on every run.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN ai_assessment jsonb;
