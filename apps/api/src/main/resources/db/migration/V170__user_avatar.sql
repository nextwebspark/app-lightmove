-- A person's profile photo, held by us rather than linked: an upload, or a copy of the picture their identity
-- provider sent. A provider's link expires (LinkedIn's within weeks) and every colleague's browser fetched it from
-- the provider; a stored copy does neither.
--
-- Account data, not tenant data: one photo per person whatever workspaces they belong to (V81), like V162's factor.
-- app_lm_user.avatar_url now names this row as /api/v1/avatars/<token>, a public link whose token is the secret (the
-- booking link's idiom); a new photo is a new token, so a cached old one is never served under the new link.
-- app_lm_user.avatar_source keeps naming who supplied it, and V25's reserved 'USER' is now written: an upload, or a
-- removal (avatar_url null), that no provider sign-in replaces.

CREATE TABLE app_lm_user_avatar (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    -- 32 random bytes, base64url without padding.
    token        varchar(43) NOT NULL,
    -- Always re-encoded by the application, never the bytes as sent, so an upload cannot carry markup or metadata.
    content      bytea       NOT NULL,
    content_type varchar(32) NOT NULL
        CONSTRAINT app_lm_user_avatar_content_type_chk CHECK (content_type = 'image/jpeg'),
    -- Of the re-encoded bytes: a provider re-signing the same picture's link keeps its token.
    sha256       varchar(64) NOT NULL,
    source       varchar(32) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_user_avatar_user_uk UNIQUE (user_id),
    CONSTRAINT app_lm_user_avatar_token_uk UNIQUE (token)
);

COMMENT ON TABLE app_lm_user_avatar IS
    'A person''s stored profile photo. Account data: one per user, read by its token or its user, never by workspace.';

CREATE TRIGGER app_lm_user_avatar_touch
    BEFORE UPDATE ON app_lm_user_avatar
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
