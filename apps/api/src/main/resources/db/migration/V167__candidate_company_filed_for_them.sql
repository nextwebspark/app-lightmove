-- Whether the company an executive is mapped at was filed into the universe for them (their capture's research, or
-- the employer the workspace knew them at), rather than already held. The plugin's Undo takes that company with the
-- person while nobody else is there; once any other path moves them, it is false again.

ALTER TABLE app_lm_project_candidate
    ADD COLUMN company_filed_for_them boolean NOT NULL DEFAULT false;
