-- Settings → Platform → Variables and Prompts (#1187): a super admin overrides an allowlisted lightmove.* value or
-- a system prompt without a release. Platform data, not tenant data: no workspace_id, read by every instance whole.
--
-- The allowlist is code (PlatformSetting), so app_lm_platform_setting carries no CHECK on its key: a key the code
-- no longer lists is ignored on load, never applied. A key with no row runs on the value bound at boot.

CREATE TABLE app_lm_platform_setting (
    setting_key varchar(96) PRIMARY KEY,
    value       varchar(200) NOT NULL,
    updated_by  uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    updated_at  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_platform_setting IS
    'Overrides of allowlisted lightmove.* values, one row per key. Reset deletes the row. Written by a super admin only.';

-- One row per saved version of a prompt, never updated but for active: a restore of an older version inserts a new
-- one with its body, and Revert to shipped clears active, so the classpath file answers again.
CREATE TABLE app_lm_platform_prompt (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_id  varchar(64) NOT NULL,
    version    int         NOT NULL CONSTRAINT app_lm_platform_prompt_version_chk CHECK (version > 0),
    body       text        NOT NULL CONSTRAINT app_lm_platform_prompt_body_chk CHECK (char_length(body) BETWEEN 1 AND 100000),
    active     boolean     NOT NULL DEFAULT false,
    created_by uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_platform_prompt_version_uk UNIQUE (prompt_id, version)
);

CREATE UNIQUE INDEX app_lm_platform_prompt_active_uk ON app_lm_platform_prompt (prompt_id) WHERE active;

COMMENT ON TABLE app_lm_platform_prompt IS
    'Saved versions of a system prompt; the active one replaces the shipped file. The body is never audited.';

-- Every save, reset, revert and Refresh raises version in the transaction that writes it. An instance compares it
-- with the version it loaded, so a NOTIFY lost while its listener reconnected still lands within the check interval.
CREATE TABLE app_lm_platform_config_state (
    id         smallint    PRIMARY KEY DEFAULT 1 CONSTRAINT app_lm_platform_config_state_single_chk CHECK (id = 1),
    version    bigint      NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO app_lm_platform_config_state (id, version) VALUES (1, 0);

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'PLATFORM_SETTINGS_MANAGE', 'Settings → Platform → Variables: change and reset allowlisted application values'),
       ('PLATFORM', 'PROMPT_MANAGE',            'Settings → Platform → Prompts: change and revert the system prompts')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('PLATFORM', 'SUPER_ADMIN', 'PLATFORM_SETTINGS_MANAGE'),
             ('PLATFORM', 'SUPER_ADMIN', 'PROMPT_MANAGE')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name
ON CONFLICT (role_id, action_id) DO NOTHING;
