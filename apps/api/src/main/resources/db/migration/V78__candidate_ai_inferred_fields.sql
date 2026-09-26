-- Which of nationality / gender / years_experience hold a value the model proposed that no researcher
-- has changed since, so a report never reads an unreviewed guess as a recorded fact.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN ai_inferred_fields jsonb NOT NULL DEFAULT '[]';
