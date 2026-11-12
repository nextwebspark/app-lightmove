-- A signature's photo is cut to a shape (baked into the picture, since Outlook ignores border-radius), any layout may
-- carry extra words under its details, and a row of the signer's own uploaded pictures may sit at its foot.
ALTER TABLE app_lm_email_signature
    ADD COLUMN photo_shape varchar(8) NOT NULL DEFAULT 'CIRCLE'
        CONSTRAINT app_lm_email_signature_photo_shape_chk CHECK (photo_shape IN ('CIRCLE', 'ROUNDED', 'SQUARE')),
    ADD COLUMN footer_pictures jsonb NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT app_lm_email_signature_footer_pictures_chk
            CHECK (jsonb_typeof(footer_pictures) = 'array' AND jsonb_array_length(footer_pictures) <= 6);

ALTER TABLE app_lm_email_signature DROP CONSTRAINT app_lm_email_signature_custom_chk;

COMMENT ON COLUMN app_lm_email_signature.custom_body IS
    'Markdown: the whole of a CUSTOM signature, or the extra words under any other layout''s details.';
COMMENT ON COLUMN app_lm_email_signature.footer_pictures IS
    'Tokens of the signer''s own SIGNATURE pictures in app_lm_email_image, drawn in a row at the signature''s foot.';
