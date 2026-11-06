-- A workspace's suppression list (#920): the people no door may file again — erased at their request, objecting to
-- being processed, or named by an admin. Every door consults it before a person is founded or mapped: the add
-- form, the plugin, a spreadsheet, Find executives and People Search.
--
-- A row holds no personal data a reader could use: value_hash is SHA-256 over the workspace id, the kind and the
-- normalised key (a LinkedIn profile slug, or an email's ledger key), so the list cannot be read back into names,
-- and the same person suppressed by two firms is two unrelated hashes. note is an admin's own words about why.

CREATE TABLE app_lm_workspace_suppression (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    kind         varchar(16) NOT NULL
        CONSTRAINT app_lm_workspace_suppression_kind_chk CHECK (kind IN ('PROFILE_SLUG', 'EMAIL')),
    value_hash   varchar(64) NOT NULL
        CONSTRAINT app_lm_workspace_suppression_hash_chk CHECK (value_hash ~ '^[0-9a-f]{64}$'),
    reason       varchar(16) NOT NULL
        CONSTRAINT app_lm_workspace_suppression_reason_chk CHECK (reason IN ('ERASED', 'OBJECTED', 'MANUAL')),
    note         varchar(500),
    created_by   uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_suppression_value_uk UNIQUE (workspace_id, kind, value_hash)
);

COMMENT ON TABLE app_lm_workspace_suppression IS
    'People no door may file again, by a hash of their profile slug or email. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_suppression_touch BEFORE UPDATE ON app_lm_workspace_suppression
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_workspace_suppression ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_suppression FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_suppression
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
