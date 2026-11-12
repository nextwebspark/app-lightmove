-- #922: the workspace decides whether the AI infers gender, infers nationality, drafts assessments, and runs on its
-- own when a person is captured or sourced. Workspaces that exist keep what they had (every switch on); a workspace
-- founded from here on starts with all four off until an admin turns one on.
ALTER TABLE app_lm_workspace
    ADD COLUMN ai_infer_gender         boolean NOT NULL DEFAULT true,
    ADD COLUMN ai_infer_nationality    boolean NOT NULL DEFAULT true,
    ADD COLUMN ai_draft_assessments    boolean NOT NULL DEFAULT true,
    ADD COLUMN ai_enrich_automatically boolean NOT NULL DEFAULT true;

ALTER TABLE app_lm_workspace
    ALTER COLUMN ai_infer_gender SET DEFAULT false,
    ALTER COLUMN ai_infer_nationality SET DEFAULT false,
    ALTER COLUMN ai_draft_assessments SET DEFAULT false,
    ALTER COLUMN ai_enrich_automatically SET DEFAULT false;
