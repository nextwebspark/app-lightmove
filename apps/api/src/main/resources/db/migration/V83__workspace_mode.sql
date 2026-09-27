-- Who a workspace hires for. COMPANY is an in-house team hiring for its own business units — what every
-- workspace so far has been; AGENCY is a search firm whose clients are separate hiring companies. The
-- mode changes labels, what a client record shows and whose persona the assistant reads — never what is
-- stored, so an admin may switch it without migrating a row.
ALTER TABLE app_lm_workspace
    ADD COLUMN mode varchar(16) NOT NULL DEFAULT 'COMPANY',
    ADD CONSTRAINT app_lm_workspace_mode_chk CHECK (mode IN ('AGENCY', 'COMPANY'));
