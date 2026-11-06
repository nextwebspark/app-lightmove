-- Email signatures, phase 1 (docs/email-signatures.md): a consultant's own signatures, the profile details they show,
-- and the pictures an email carries.

-- How a consultant is reached, shown in their signature. Account data like the title: one person, every workspace.
ALTER TABLE app_lm_user
    ADD COLUMN phone        varchar(40),
    ADD COLUMN linkedin_url varchar(255);

-- A picture an email shows: a copy of the person's profile photo (V170's avatar, drawn small) or of a workspace's logo,
-- made the first time a signature draws it. A recipient's mail client fetches it with no session, so it is served at
-- /api/v1/email-images/{token} to anyone holding the token: 256 random bits, one per stored picture and shared by
-- every email that shows it, never minted per message or recipient, so a fetch identifies nobody. The bytes are in
-- object storage (V105's rule), re-encoded on the way in, so no metadata ever leaves. A row outlives the photo or logo
-- it was copied from — an email already sent names its token — and is never rewritten. `source_sha256` is what it was
-- copied from: the avatar's own hash, or the logo's address hashed, so each is copied once per owner.
CREATE TABLE app_lm_email_image (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    token         varchar(64)  NOT NULL UNIQUE,
    kind          varchar(16)  NOT NULL
        CONSTRAINT app_lm_email_image_kind_chk CHECK (kind IN ('PHOTO', 'LOGO')),
    user_id       uuid         REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id  uuid         REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    source_sha256 varchar(64)  NOT NULL,
    content_type  varchar(32)  NOT NULL
        CONSTRAINT app_lm_email_image_type_chk CHECK (content_type IN ('image/png', 'image/jpeg')),
    width         integer      NOT NULL CONSTRAINT app_lm_email_image_width_chk CHECK (width > 0),
    height        integer      NOT NULL CONSTRAINT app_lm_email_image_height_chk CHECK (height > 0),
    size_bytes    integer      NOT NULL CONSTRAINT app_lm_email_image_size_chk CHECK (size_bytes > 0),
    storage_key   varchar(512) NOT NULL UNIQUE,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_email_image_owner_chk CHECK (
        (kind = 'PHOTO' AND user_id IS NOT NULL AND workspace_id IS NULL)
        OR (kind = 'LOGO' AND workspace_id IS NOT NULL AND user_id IS NULL))
);

COMMENT ON TABLE app_lm_email_image IS
    'Pictures an email carries (a copy of a person''s photo, a workspace''s logo), served publicly by their token; the bytes are in object storage under storage_key.';

CREATE UNIQUE INDEX app_lm_email_image_photo_uk ON app_lm_email_image (user_id, source_sha256) WHERE kind = 'PHOTO';
CREATE UNIQUE INDEX app_lm_email_image_logo_uk ON app_lm_email_image (workspace_id, source_sha256) WHERE kind = 'LOGO';

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

-- Row-level security (V184, V190): a signature is its workspace's; a logo is its workspace's, and a photo — account
-- data with no workspace, a copy of the person's own avatar — is read wherever its person signs in. FORCE, for V184's
-- reason: lm_app still owns these tables until #256.
ALTER TABLE app_lm_email_signature ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_email_signature FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_email_signature
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

ALTER TABLE app_lm_email_image ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_email_image FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_email_image
    USING (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id))
    WITH CHECK (workspace_id IS NULL OR app_lm_tenant_visible(workspace_id));

-- The public picture route arrives with a token and no workspace (V189's idiom): which workspace holds it, never the
-- row. A photo answers a null workspace and is read as it is.
CREATE FUNCTION app_lm_resolve_email_image(p_token text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT i.id, i.workspace_id FROM app_lm_email_image i WHERE i.token = p_token;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION app_lm_resolve_email_image(text) FROM PUBLIC;
