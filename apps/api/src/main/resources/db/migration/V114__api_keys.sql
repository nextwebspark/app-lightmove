-- API keys for the public API (epic #690, story #684).
--
-- A PERSONAL key is one member's: it can never read more than its owner can open at the moment it is used,
-- and it dies with their membership. A SERVICE key is the workspace's, made by an admin, and reads every
-- position. Only the SHA-256 of the secret is stored; the secret itself is shown once, at creation. Every key
-- expires. Scopes are the wire tokens the public API checks, a non-empty subset of the five below.

CREATE TABLE app_lm_api_key (
    id              uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    kind            varchar(16)   NOT NULL
        CONSTRAINT app_lm_api_key_kind_chk CHECK (kind IN ('PERSONAL', 'SERVICE')),
    owner_user_id   uuid          REFERENCES app_lm_user (id) ON DELETE CASCADE,
    created_by      uuid          REFERENCES app_lm_user (id) ON DELETE SET NULL,
    name            varchar(80)   NOT NULL,
    token_hash      varchar(64)   NOT NULL
        CONSTRAINT app_lm_api_key_token_hash_uk UNIQUE,
    -- The prefix and a few characters from each end, so a key can be recognised in a list without being usable.
    token_hint      varchar(32)   NOT NULL,
    scopes          jsonb         NOT NULL
        CONSTRAINT app_lm_api_key_scopes_chk CHECK (
            jsonb_typeof(scopes) = 'array'
            AND jsonb_array_length(scopes) > 0
            AND scopes <@ '["projects:read", "companies:read", "candidates:read",
                             "candidates.contacts:read", "candidates.compensation:read"]'::jsonb),
    expires_at      timestamptz   NOT NULL,
    last_used_at    timestamptz,
    last_used_ip    varchar(45),
    revoked_at      timestamptz,
    revoked_by      uuid          REFERENCES app_lm_user (id) ON DELETE SET NULL,
    revoked_reason  varchar(32)
        CONSTRAINT app_lm_api_key_revoked_reason_chk
            CHECK (revoked_reason IN ('REVOKED', 'MEMBER_REMOVED', 'WORKSPACE_DELETED')),
    created_at      timestamptz   NOT NULL DEFAULT now(),
    updated_at      timestamptz   NOT NULL DEFAULT now(),
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_api_key_owner_chk
        CHECK ((kind = 'PERSONAL') = (owner_user_id IS NOT NULL)),
    CONSTRAINT app_lm_api_key_revoked_chk
        CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL))
);

COMMENT ON TABLE app_lm_api_key IS
    'Bearer keys for /api/v1/public/**, stored as SHA-256 only. Tenant data: every read filters by workspace_id.';

CREATE INDEX app_lm_api_key_workspace_idx ON app_lm_api_key (workspace_id, created_at DESC);
CREATE INDEX app_lm_api_key_active_owner_idx ON app_lm_api_key (workspace_id, owner_user_id)
    WHERE revoked_at IS NULL;

CREATE TRIGGER app_lm_api_key_touch BEFORE UPDATE ON app_lm_api_key
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Making and revoking one's own personal key: every staff member, never a client representative.
-- A SERVICE key, and anyone else's key, asks WORKSPACE_MANAGE on top.
INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'API_KEY_MANAGE', 'API keys: create, list and revoke your own personal keys for the public API')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('WORKSPACE', 'ADMIN',  'API_KEY_MANAGE'),
             ('WORKSPACE', 'MEMBER', 'API_KEY_MANAGE')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name
ON CONFLICT (role_id, action_id) DO NOTHING;
