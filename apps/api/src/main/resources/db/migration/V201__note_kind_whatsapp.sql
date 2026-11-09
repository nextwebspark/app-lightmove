-- A note can record a WhatsApp exchange — a message, a voice note, a reply — beside a call, a meeting, an email or a
-- LinkedIn message, on a person and on a company alike.
ALTER TABLE app_lm_person_note
    DROP CONSTRAINT app_lm_person_note_kind_chk,
    ADD CONSTRAINT app_lm_person_note_kind_chk
        CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL', 'LINKEDIN', 'WHATSAPP'));

ALTER TABLE app_lm_company_note
    DROP CONSTRAINT app_lm_company_note_kind_chk,
    ADD CONSTRAINT app_lm_company_note_kind_chk
        CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL', 'LINKEDIN', 'WHATSAPP'));
