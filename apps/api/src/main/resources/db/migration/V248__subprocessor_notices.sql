-- Sub-processor change notices (#1491, decision F7): the contract promises 30 days' notice of a new sub-processor, by
-- email to each workspace's admins, who may object until it takes effect (docs/gdpr/subprocessors.md).
--
-- The notice is a platform table: written from the console, read by every workspace, no workspace of its own. Who was
-- emailed and each workspace's objection are tenant tables under forced row-level security. An objection is the
-- customer's correspondence about the contract, never people data; the console reads one workspace at a time, each
-- named by the resolver below.

CREATE TABLE app_lm_subprocessor_notice (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    provider             varchar(120) NOT NULL,
    receives             varchar(200) NOT NULL,
    purpose              varchar(200) NOT NULL,
    region               varchar(80)  NOT NULL,
    effective_on         date         NOT NULL,
    list_url             varchar(500) NOT NULL
        CONSTRAINT app_lm_subprocessor_notice_list_url_chk CHECK (list_url LIKE 'https://%'),
    published_by_user_id uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    published_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_subprocessor_notice_period_chk
        CHECK (effective_on >= (published_at AT TIME ZONE 'UTC')::date + 30)
);

CREATE INDEX app_lm_subprocessor_notice_effective_idx ON app_lm_subprocessor_notice (effective_on DESC);

COMMENT ON TABLE app_lm_subprocessor_notice IS
    'A new sub-processor announced from the platform console, at least 30 days before it takes effect. Platform data.';

-- One row per admin a notice was owed to in a workspace, inserted before the email goes so a second pass or a second
-- instance sends nothing twice. A row with no sent_at is retried at next_attempt_at until the notice takes effect;
-- next_attempt_at null is given up (the admin left, or the workspace went).
CREATE TABLE app_lm_subprocessor_notice_email (
    notice_id       uuid        NOT NULL REFERENCES app_lm_subprocessor_notice (id) ON DELETE CASCADE,
    workspace_id    uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id         uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    attempts        smallint    NOT NULL DEFAULT 1,
    next_attempt_at timestamptz,
    sent_at         timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (notice_id, workspace_id, user_id)
);

CREATE INDEX app_lm_subprocessor_notice_email_owed_idx
    ON app_lm_subprocessor_notice_email (next_attempt_at) WHERE sent_at IS NULL AND next_attempt_at IS NOT NULL;

COMMENT ON TABLE app_lm_subprocessor_notice_email IS
    'Which workspace admins a sub-processor notice was emailed to, and the retries of one that failed.';

ALTER TABLE app_lm_subprocessor_notice_email ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_subprocessor_notice_email FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_subprocessor_notice_email
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- A workspace's one objection to one notice. An admin may change it until the notice takes effect; the row then says
-- who last wrote it and when.
CREATE TABLE app_lm_subprocessor_objection (
    id                  uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    notice_id           uuid          NOT NULL REFERENCES app_lm_subprocessor_notice (id) ON DELETE CASCADE,
    objected_by_user_id uuid          REFERENCES app_lm_user (id) ON DELETE SET NULL,
    reason              varchar(2000) NOT NULL
        CONSTRAINT app_lm_subprocessor_objection_reason_chk CHECK (btrim(reason) <> ''),
    objected_at         timestamptz   NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_subprocessor_objection_uk UNIQUE (workspace_id, notice_id)
);

CREATE INDEX app_lm_subprocessor_objection_notice_idx ON app_lm_subprocessor_objection (notice_id);

COMMENT ON TABLE app_lm_subprocessor_objection IS
    'A workspace admin''s objection to a sub-processor notice: contract correspondence, read by Uncava in the console.';

ALTER TABLE app_lm_subprocessor_objection ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_subprocessor_objection FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_subprocessor_objection
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The console's read of a notice's objections (V191's idiom): the workspaces holding one, each then read in its scope.
CREATE FUNCTION app_lm_workspaces_with_subprocessor_objections(p_notice_id uuid)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT o.workspace_id FROM app_lm_subprocessor_objection o WHERE o.notice_id = p_notice_id;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The retry pass's walk: an email not yet sent, due again, for a notice not yet in effect. Wider than the claim, never
-- narrower.
CREATE FUNCTION app_lm_workspaces_with_subprocessor_emails_owed(p_at timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT e.workspace_id
          FROM app_lm_subprocessor_notice_email e
          JOIN app_lm_subprocessor_notice n ON n.id = e.notice_id
         WHERE e.sent_at IS NULL AND e.next_attempt_at <= p_at
           AND n.effective_on > (p_at AT TIME ZONE 'UTC')::date;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- The console's list: per notice, how many admins were emailed, how many are still owed one (not sent and not given
-- up), and how many workspaces objected. Counts only, never which workspace or who.
CREATE FUNCTION app_lm_count_subprocessor_notice_replies()
    RETURNS TABLE (notice_id uuid, emailed bigint, owed bigint, objections bigint)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT n.id,
               (SELECT count(*) FROM app_lm_subprocessor_notice_email e
                 WHERE e.notice_id = n.id AND e.sent_at IS NOT NULL),
               (SELECT count(*) FROM app_lm_subprocessor_notice_email e
                 WHERE e.notice_id = n.id AND e.sent_at IS NULL AND e.next_attempt_at IS NOT NULL),
               (SELECT count(*) FROM app_lm_subprocessor_objection o WHERE o.notice_id = n.id)
          FROM app_lm_subprocessor_notice n;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION
    app_lm_workspaces_with_subprocessor_objections(uuid),
    app_lm_workspaces_with_subprocessor_emails_owed(timestamptz),
    app_lm_count_subprocessor_notice_replies()
    FROM PUBLIC;

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'SUBPROCESSOR_NOTICE_MANAGE',
        'Publish a sub-processor change to every workspace admin, and read the objections to it')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PLATFORM' AND a.name = 'SUBPROCESSOR_NOTICE_MANAGE'
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
