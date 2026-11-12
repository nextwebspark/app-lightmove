-- A Custom signature's own pictures (a group's logo, a membership badge): uploaded by the person, kept as the photo and
-- logo are, and drawn only in that person's signatures in that workspace. A picture is the person's in one workspace,
-- as a signature is, so it carries both ids and V193's policy (workspace_id IS NULL OR visible) already fences it.
ALTER TABLE app_lm_email_image DROP CONSTRAINT app_lm_email_image_kind_chk;
ALTER TABLE app_lm_email_image ADD CONSTRAINT app_lm_email_image_kind_chk
    CHECK (kind IN ('PHOTO', 'LOGO', 'SIGNATURE'));

ALTER TABLE app_lm_email_image DROP CONSTRAINT app_lm_email_image_owner_chk;
ALTER TABLE app_lm_email_image ADD CONSTRAINT app_lm_email_image_owner_chk CHECK (
    (kind = 'PHOTO' AND user_id IS NOT NULL AND workspace_id IS NULL)
    OR (kind = 'LOGO' AND workspace_id IS NOT NULL AND user_id IS NULL)
    OR (kind = 'SIGNATURE' AND workspace_id IS NOT NULL AND user_id IS NOT NULL));

-- The same file uploaded twice is one picture.
CREATE UNIQUE INDEX app_lm_email_image_signature_uk ON app_lm_email_image (workspace_id, user_id, source_sha256)
    WHERE kind = 'SIGNATURE';

COMMENT ON TABLE app_lm_email_image IS
    'Pictures an email carries (a copy of a person''s photo, a workspace''s logo, a picture a person uploaded into their signature), served publicly by their token; the bytes are in object storage under storage_key.';
