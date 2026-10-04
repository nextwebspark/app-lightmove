-- A consultant's own Zoom account, for the video link Book a call puts on an invite (epic #642, story #648).
--
-- Separate from the mailbox: a consultant may connect either without the other, and Zoom is never a place mail goes.
-- The refresh token is held encrypted (core/crypto, bound to the workspace and the consultant), never returned or
-- logged, exactly as a direct mailbox's is. zoom_user_id is Zoom's own id for the account, so a reconnect to another
-- Zoom account is told apart from one to the same. ERROR: Zoom refused the refresh token; reconnecting clears it.
-- A connection in flight is a row of app_lm_mailbox_authorization with provider 'zoom'.

CREATE TABLE app_lm_zoom_connection (
    id                      uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id            uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id                 uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    zoom_user_id            varchar(64)  NOT NULL,
    refresh_token_encrypted text         NOT NULL,
    status                  varchar(16)  NOT NULL
        CONSTRAINT app_lm_zoom_connection_status_chk CHECK (status IN ('ACTIVE', 'ERROR')),
    connected_at            timestamptz  NOT NULL,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    version                 bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_zoom_connection_member_uk UNIQUE (workspace_id, user_id)
);

COMMENT ON TABLE app_lm_zoom_connection IS
    'A consultant''s own Zoom account, connected for meeting links. Tenant data: every read filters by workspace_id and user_id.';

CREATE TRIGGER app_lm_zoom_connection_touch BEFORE UPDATE ON app_lm_zoom_connection
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
