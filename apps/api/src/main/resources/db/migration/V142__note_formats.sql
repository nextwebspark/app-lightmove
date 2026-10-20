-- A note keeps how its words were stored: as typed (every note before this, and every one a door files) or as the
-- editor's Markdown. Kept beside the words rather than guessed from them, so a stray "*" or "1." in an old or
-- imported note is never read as formatting. Formatting marks and mention tokens count against the limit, so a
-- person's note may now hold 10,000 characters.
ALTER TABLE app_lm_person_note
    ADD COLUMN body_format varchar(16) NOT NULL DEFAULT 'PLAIN'
        CONSTRAINT app_lm_person_note_body_format_chk CHECK (body_format IN ('PLAIN', 'MARKDOWN'));

ALTER TABLE app_lm_person_note DROP CONSTRAINT app_lm_person_note_body_chk;
ALTER TABLE app_lm_person_note
    ADD CONSTRAINT app_lm_person_note_body_chk CHECK (btrim(body) <> '' AND char_length(body) <= 10000);

ALTER TABLE app_lm_client
    ADD COLUMN notes_format varchar(16) NOT NULL DEFAULT 'PLAIN'
        CONSTRAINT app_lm_client_notes_format_chk CHECK (notes_format IN ('PLAIN', 'MARKDOWN'));
