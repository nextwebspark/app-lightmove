-- Outreach email goes from a consultant's own Gmail or Outlook (epic #620, story #622).
--
-- A mailbox is connected through Nylas's hosted sign-in, and what comes back is a grant id: Nylas's
-- handle for that mailbox, useless without our API key. We hold the grant, never the provider's
-- OAuth tokens. One mailbox per person per workspace, since every email goes from the sender's own
-- address.

CREATE TABLE app_lm_mailbox_connection (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id         uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    -- Nylas's name for the mailbox's host (google, microsoft), as it answered it.
    provider        varchar(32)  NOT NULL,
    address         varchar(320) NOT NULL,
    grant_id        varchar(128) NOT NULL,
    -- ERROR: the provider withdrew access (a password change, an admin removing the app); reconnecting clears it.
    status          varchar(16)  NOT NULL
        CONSTRAINT app_lm_mailbox_connection_status_chk CHECK (status IN ('ACTIVE', 'ERROR')),
    daily_cap       integer      NOT NULL CONSTRAINT app_lm_mailbox_connection_daily_cap_chk CHECK (daily_cap > 0),
    connected_at    timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_mailbox_connection_member_uk UNIQUE (workspace_id, user_id)
);

COMMENT ON TABLE app_lm_mailbox_connection IS
    'A consultant''s own mailbox, connected for outreach. Tenant data: every read filters by workspace_id and user_id.';

CREATE TRIGGER app_lm_mailbox_connection_touch BEFORE UPDATE ON app_lm_mailbox_connection
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- A connection started and not yet back from the provider's consent screen. The state is stored as its
-- SHA-256 only, single-use, and expires; the browser that started it holds the raw value in a cookie,
-- so a consent link handed to someone else connects nothing.
CREATE TABLE app_lm_mailbox_authorization (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash      varchar(64)  NOT NULL CONSTRAINT app_lm_mailbox_authorization_state_uk UNIQUE,
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id         uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    provider        varchar(32)  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

CREATE INDEX app_lm_mailbox_authorization_user_idx ON app_lm_mailbox_authorization (user_id);
