-- A signature's photo is cut to a shape (baked into the picture, since Outlook ignores border-radius), and any layout
-- may carry extra words, pictures among them, under its details.
ALTER TABLE app_lm_email_signature
    ADD COLUMN photo_shape varchar(8) NOT NULL DEFAULT 'CIRCLE'
        CONSTRAINT app_lm_email_signature_photo_shape_chk CHECK (photo_shape IN ('CIRCLE', 'ROUNDED', 'SQUARE'));

ALTER TABLE app_lm_email_signature DROP CONSTRAINT app_lm_email_signature_custom_chk;

COMMENT ON COLUMN app_lm_email_signature.custom_body IS
    'Markdown: the whole of a CUSTOM signature, or the extra words under any other layout''s details.';
