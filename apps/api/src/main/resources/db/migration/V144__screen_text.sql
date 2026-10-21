-- The words on every screen, edited by a super admin (Settings → Platform → Screen text).
--
-- The default for each sentence ships in the SPA's code under a stable key; these tables hold only what someone
-- changed. A draft is one key's proposed text; publishing folds every draft into a release, the whole set of
-- overrides as of then, so rolling back is copying an old release forward. Not tenant-scoped, for V49's reason:
-- one wording reaches every workspace, and none of it is client data.

CREATE TABLE app_lm_screen_text_draft (
    id         uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    locale     varchar(16)  NOT NULL,
    text_key   varchar(160) NOT NULL,
    text       text         NOT NULL
        CONSTRAINT app_lm_screen_text_draft_text_chk CHECK (btrim(text) <> '' AND char_length(text) <= 2000),
    updated_by uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_screen_text_draft_key_uk UNIQUE (locale, text_key)
);

CREATE TRIGGER app_lm_screen_text_draft_touch BEFORE UPDATE ON app_lm_screen_text_draft
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Never updated: a release is what the screens read from the moment it was published until the next one.
CREATE TABLE app_lm_screen_text_release (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    locale        varchar(16) NOT NULL,
    number        integer     NOT NULL CONSTRAINT app_lm_screen_text_release_number_chk CHECK (number > 0),
    entries       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    changed_keys  jsonb       NOT NULL DEFAULT '[]'::jsonb,
    note          varchar(500),
    restored_from integer,
    published_by  uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    published_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_screen_text_release_number_uk UNIQUE (locale, number)
);

COMMENT ON TABLE app_lm_screen_text_release IS
    'Published screen text: every override as of one publish, keyed by the SPA catalogue''s keys. Not tenant data.';

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'SCREEN_TEXT_MANAGE', 'Edit and publish the words on every screen, error messages included')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = r.scope AND a.name = 'SCREEN_TEXT_MANAGE'
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
