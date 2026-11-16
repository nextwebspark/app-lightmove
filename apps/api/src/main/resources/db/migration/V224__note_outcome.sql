-- A logged call or WhatsApp message may say how it went (#1337), in the sequence call step's own words. Once it does,
-- the note may stay empty: "No answer" is the whole story of most calls.
ALTER TABLE app_lm_person_note
    ADD COLUMN outcome varchar(16)
        CONSTRAINT app_lm_person_note_outcome_chk CHECK (
            outcome IS NULL
            OR (kind = 'CALL' AND outcome IN ('CONNECTED', 'VOICEMAIL', 'NO_ANSWER', 'WRONG_NUMBER'))
            OR (kind = 'WHATSAPP' AND outcome IN ('SENT', 'REPLIED')));

ALTER TABLE app_lm_person_note DROP CONSTRAINT app_lm_person_note_body_chk;
ALTER TABLE app_lm_person_note
    ADD CONSTRAINT app_lm_person_note_body_chk
        CHECK (char_length(body) <= 10000 AND (btrim(body) <> '' OR outcome IS NOT NULL));
