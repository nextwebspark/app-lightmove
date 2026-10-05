-- The MCP server's OAuth 2.1 authorization server (epic #699, story #701).
--
-- Spring's own JDBC services hard-code oauth2_* table names and keep every token raw, so these tables back our own
-- implementations of its three core interfaces instead. Every token — the authorization request's state, the code,
-- the access token and the refresh token — is stored as its SHA-256 only.
--
-- A client is always public (no secret, PKCE required) and may use only the authorization code and refresh token
-- grants; that is fixed in code, so nothing here records it.

CREATE TABLE app_lm_oauth_client (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    -- A client id document's URL (#702) can be long, so text rather than a short varchar.
    client_id       text          NOT NULL
        CONSTRAINT app_lm_oauth_client_client_id_uk UNIQUE,
    client_name     varchar(200)  NOT NULL,
    client_uri      text,
    logo_uri        text,
    redirect_uris   jsonb         NOT NULL
        CONSTRAINT app_lm_oauth_client_redirect_uris_chk
            CHECK (jsonb_typeof(redirect_uris) = 'array' AND jsonb_array_length(redirect_uris) > 0),
    scopes          jsonb         NOT NULL
        CONSTRAINT app_lm_oauth_client_scopes_chk CHECK (
            jsonb_typeof(scopes) = 'array'
            AND jsonb_array_length(scopes) > 0
            AND scopes <@ '["projects:read", "companies:read", "candidates:read",
                             "candidates.contacts:read", "candidates.compensation:read"]'::jsonb),
    source          varchar(16)   NOT NULL
        CONSTRAINT app_lm_oauth_client_source_chk CHECK (source IN ('SEEDED', 'DCR', 'CIMD')),
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         bigint        NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_oauth_client_touch BEFORE UPDATE ON app_lm_oauth_client
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- One grant: a user connecting one client to one workspace. workspace_id is null only while the request waits for
-- consent; deleting the row is revoking the grant.
CREATE TABLE app_lm_oauth_authorization (
    id                          uuid          PRIMARY KEY,
    client_id                   uuid          NOT NULL REFERENCES app_lm_oauth_client (id) ON DELETE CASCADE,
    user_id                     uuid          NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id                uuid          REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    authorization_grant_type    varchar(32)   NOT NULL,
    authorized_scopes           jsonb         NOT NULL DEFAULT '[]'::jsonb,
    -- The framework's own attributes (the authorization request, the principal), as its Jackson modules write them.
    attributes                  text          NOT NULL,
    state_hash                  varchar(64)
        CONSTRAINT app_lm_oauth_authorization_state_uk UNIQUE,
    code_hash                   varchar(64)
        CONSTRAINT app_lm_oauth_authorization_code_uk UNIQUE,
    code_issued_at              timestamptz,
    code_expires_at             timestamptz,
    code_metadata               text,
    access_token_hash           varchar(64)
        CONSTRAINT app_lm_oauth_authorization_access_token_uk UNIQUE,
    access_token_issued_at      timestamptz,
    access_token_expires_at     timestamptz,
    access_token_scopes         jsonb,
    access_token_metadata       text,
    refresh_token_hash          varchar(64)
        CONSTRAINT app_lm_oauth_authorization_refresh_token_uk UNIQUE,
    refresh_token_issued_at     timestamptz,
    refresh_token_expires_at    timestamptz,
    refresh_token_metadata      text,
    consented_at                timestamptz,
    last_used_at                timestamptz,
    created_at                  timestamptz   NOT NULL DEFAULT now(),
    updated_at                  timestamptz   NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_oauth_authorization IS
    'OAuth grants to MCP clients, tokens as SHA-256 only. Tenant data: every read filters by workspace_id.';

CREATE INDEX app_lm_oauth_authorization_member_idx ON app_lm_oauth_authorization (workspace_id, user_id);
CREATE INDEX app_lm_oauth_authorization_expiry_idx
    ON app_lm_oauth_authorization (GREATEST(code_expires_at, access_token_expires_at, refresh_token_expires_at));

CREATE TRIGGER app_lm_oauth_authorization_touch BEFORE UPDATE ON app_lm_oauth_authorization
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- A refresh token that has been rotated away. Presenting one again is the theft signature: the grant is revoked.
CREATE TABLE app_lm_oauth_retired_refresh_token (
    token_hash          varchar(64)   PRIMARY KEY,
    authorization_id    uuid          NOT NULL REFERENCES app_lm_oauth_authorization (id) ON DELETE CASCADE,
    retired_at          timestamptz   NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_oauth_retired_refresh_token_authorization_idx
    ON app_lm_oauth_retired_refresh_token (authorization_id);
CREATE INDEX app_lm_oauth_retired_refresh_token_retired_idx
    ON app_lm_oauth_retired_refresh_token (retired_at);
