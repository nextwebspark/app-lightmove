-- Email templates, phase 1 (docs/email-templates.md, epic #1247): a consultant's saved emails and replies, personal or
-- shared with the workspace, and which template an own email came from.

-- A template is its owner's; shared_at set lets every staff member of the workspace apply it, and only its owner or a
-- WORKSPACE_MANAGE holder change it. Deleting an account deletes that person's templates, shared ones included, as
-- signatures do; a member's removal deletes nothing (V81 rejoins the same membership row). The body is the notes
-- editor's Markdown, so no format column is kept.
CREATE TABLE app_lm_email_template (
    id            uuid           PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid           NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    owner_user_id uuid           NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    name          varchar(80)    NOT NULL
        CONSTRAINT app_lm_email_template_name_chk CHECK (btrim(name) <> ''),
    subject       varchar(200)
        CONSTRAINT app_lm_email_template_subject_chk CHECK (subject !~ '[\r\n]'),
    body          varchar(10000) NOT NULL
        CONSTRAINT app_lm_email_template_body_chk CHECK (btrim(body) <> ''),
    shared_at     timestamptz,
    updated_by    uuid           REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    version       bigint         NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_email_template IS
    'Saved emails a consultant applies in the composer. Tenant data: a personal one (shared_at null) is read by its owner alone; a shared one by the workspace''s staff.';

CREATE INDEX app_lm_email_template_owner_idx ON app_lm_email_template (workspace_id, owner_user_id);
CREATE INDEX app_lm_email_template_shared_idx ON app_lm_email_template (workspace_id) WHERE shared_at IS NOT NULL;

CREATE TRIGGER app_lm_email_template_touch BEFORE UPDATE ON app_lm_email_template
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Row-level security (V184, V190) keeps a template in its workspace; keeping a personal one private is the service's,
-- every personal read keyed on the caller.
ALTER TABLE app_lm_email_template ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_email_template FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_email_template
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The template an own email was written from, for usage figures later. Written from phase 2, and only for a template the
-- sender could see in their own workspace: the reference check runs as the table's owner, so the key alone would not
-- keep a template of another workspace out.
ALTER TABLE app_lm_outreach_message
    ADD COLUMN template_id uuid REFERENCES app_lm_email_template (id) ON DELETE SET NULL;

CREATE INDEX app_lm_outreach_message_template_idx ON app_lm_outreach_message (template_id)
    WHERE template_id IS NOT NULL;
