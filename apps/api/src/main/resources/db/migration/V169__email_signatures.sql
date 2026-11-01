-- Email signatures, phase 1 (docs/email-signatures.md): a consultant's own signatures, the profile details they show,
-- and the pictures an email carries.

-- How a consultant is reached, shown in their signature. Account data like the title: one person, every workspace.
ALTER TABLE app_lm_user
    ADD COLUMN phone        varchar(40),
    ADD COLUMN linkedin_url varchar(255);

-- A picture an email shows: the person's uploaded photo, or a workspace's logo copied in from where it was published.
-- A recipient's mail client fetches it with no session, so it is served at /api/v1/email-images/{token} to anyone holding
-- the token, which is the only credential: 256 random bits, never derived from anything. The bytes are in object storage
-- (V105's rule), re-encoded on the way in, so no metadata a camera wrote ever leaves. A row outlives the photo or logo it
-- was — an email already sent still names its token — and is never rewritten.
CREATE TABLE app_lm_email_image (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    token             varchar(64)  NOT NULL UNIQUE,
    kind              varchar(16)  NOT NULL
        CONSTRAINT app_lm_email_image_kind_chk CHECK (kind IN ('PHOTO', 'LOGO')),
    user_id           uuid         REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id      uuid         REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    -- The address a logo was copied from, hashed: the same logo is copied once per workspace.
    source_url_sha256 varchar(64),
    content_type      varchar(32)  NOT NULL
        CONSTRAINT app_lm_email_image_type_chk CHECK (content_type IN ('image/png', 'image/jpeg')),
    width             integer      NOT NULL CONSTRAINT app_lm_email_image_width_chk CHECK (width > 0),
    height            integer      NOT NULL CONSTRAINT app_lm_email_image_height_chk CHECK (height > 0),
    size_bytes        integer      NOT NULL CONSTRAINT app_lm_email_image_size_chk CHECK (size_bytes > 0),
    storage_key       varchar(512) NOT NULL UNIQUE,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_email_image_owner_chk CHECK (
        (kind = 'PHOTO' AND user_id IS NOT NULL AND workspace_id IS NULL AND source_url_sha256 IS NULL)
        OR (kind = 'LOGO' AND workspace_id IS NOT NULL AND user_id IS NULL AND source_url_sha256 IS NOT NULL))
);

COMMENT ON TABLE app_lm_email_image IS
    'Pictures an email carries (a person''s photo, a workspace''s logo), served publicly by their token; the bytes are in object storage under storage_key.';

CREATE UNIQUE INDEX app_lm_email_image_logo_uk ON app_lm_email_image (workspace_id, source_url_sha256)
    WHERE kind = 'LOGO';
CREATE INDEX app_lm_email_image_user_idx ON app_lm_email_image (user_id) WHERE user_id IS NOT NULL;

-- A consultant's signatures in one workspace — their title and firm can differ between workspaces. `fields` holds only
-- what this signature overrides; a field it leaves out follows the profile or the workspace when it is drawn.
CREATE TABLE app_lm_email_signature (
    id                uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id           uuid          NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    name              varchar(60)   NOT NULL
        CONSTRAINT app_lm_email_signature_name_chk CHECK (btrim(name) <> ''),
    layout            varchar(16)   NOT NULL
        CONSTRAINT app_lm_email_signature_layout_chk
            CHECK (layout IN ('CLASSIC', 'PHOTO', 'PHOTO_LOGO', 'LOGO_BANNER', 'MINIMAL', 'CUSTOM')),
    fields            jsonb         NOT NULL DEFAULT '{}'::jsonb,
    show_photo        boolean       NOT NULL DEFAULT false,
    show_logo         boolean       NOT NULL DEFAULT false,
    show_booking_link boolean       NOT NULL DEFAULT false,
    custom_body       varchar(2000),
    disclaimer        varchar(1000),
    is_default        boolean       NOT NULL DEFAULT false,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_email_signature_custom_chk CHECK (layout = 'CUSTOM' OR custom_body IS NULL)
);

COMMENT ON TABLE app_lm_email_signature IS
    'A consultant''s email signatures. Tenant data: every read filters by workspace_id and the caller''s own user_id.';

CREATE UNIQUE INDEX app_lm_email_signature_default_uk ON app_lm_email_signature (workspace_id, user_id)
    WHERE is_default;
CREATE INDEX app_lm_email_signature_owner_idx ON app_lm_email_signature (workspace_id, user_id);

CREATE TRIGGER app_lm_email_signature_touch BEFORE UPDATE ON app_lm_email_signature
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
