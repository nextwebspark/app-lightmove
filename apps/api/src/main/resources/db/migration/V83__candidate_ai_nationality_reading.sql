-- The nationality classifier's last reading: category (a group or Unknown), confidence, the evidence
-- for and against, and the rule applied. Replaced whole per run. Staff-only — the evidence reasons
-- about a person's origin, so it is served on the AI assessment read and never on the candidate.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN ai_nationality_reading jsonb;
