-- How a workspace connects mail, calendar and video providers (epic #642, story #643).
--
-- Every provider is reached through an OAuth app. By default that is Uncava's own (SHARED, configured per
-- deployment and never stored here); a workspace whose IT department will not admit a third party's app
-- registers its own and pastes the keys in (OWN). No row means SHARED. The client secret is stored only
-- encrypted (core/crypto: an envelope under a key held in Secret Manager, bound to this workspace and
-- provider) and is never returned by any read.

CREATE TABLE app_lm_workspace_mail_integration (
    id                       uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id             uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    provider                 varchar(16)  NOT NULL
        CONSTRAINT app_lm_workspace_mail_integration_provider_chk CHECK (provider IN ('GOOGLE', 'MICROSOFT', 'ZOOM')),
    mode                     varchar(16)  NOT NULL
        CONSTRAINT app_lm_workspace_mail_integration_mode_chk CHECK (mode IN ('SHARED', 'OWN')),
    client_id                varchar(255),
    client_secret_encrypted  text,
    -- The customer's Entra directory: a single-tenant app signs in at login.microsoftonline.com/{tenant}.
    tenant_id                varchar(64),
    -- The secret's own expiry, as the admin read it off the provider's console, so the page can warn ahead of it.
    secret_expires_at        timestamptz,
    updated_by               uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    version                  bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_mail_integration_provider_uk UNIQUE (workspace_id, provider),
    CONSTRAINT app_lm_workspace_mail_integration_own_keys_chk
        CHECK (mode = 'SHARED' OR (client_id IS NOT NULL AND client_secret_encrypted IS NOT NULL)),
    CONSTRAINT app_lm_workspace_mail_integration_shared_clear_chk
        CHECK (mode = 'OWN' OR (client_id IS NULL AND client_secret_encrypted IS NULL
                                AND tenant_id IS NULL AND secret_expires_at IS NULL)),
    CONSTRAINT app_lm_workspace_mail_integration_tenant_chk
        CHECK (tenant_id IS NULL OR provider = 'MICROSOFT')
);

COMMENT ON TABLE app_lm_workspace_mail_integration IS
    'A workspace''s choice of OAuth app per provider: Uncava''s shared app, or its own with an encrypted secret. '
    'Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_mail_integration_touch BEFORE UPDATE ON app_lm_workspace_mail_integration
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- How a workspace's calendar events are read (epic #642, "Calendar events through Recall"). RECALL hands the
-- app's keys and each user's calendar refresh token to Recall.ai, which pushes changes back; DIRECT keeps them
-- here and reads a calendar only when a drawer opens, for an IT department that will not let them leave.
ALTER TABLE app_lm_workspace
    ADD COLUMN calendar_sync varchar(16) NOT NULL DEFAULT 'RECALL'
        CONSTRAINT app_lm_workspace_calendar_sync_chk CHECK (calendar_sync IN ('RECALL', 'DIRECT'));
