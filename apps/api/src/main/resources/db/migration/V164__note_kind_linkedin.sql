-- A note can record a LinkedIn exchange — a message, a connection request, a reply — beside a call, a meeting or an
-- email, on a person and on a company alike.
ALTER TABLE app_lm_person_note
    DROP CONSTRAINT app_lm_person_note_kind_chk,
    ADD CONSTRAINT app_lm_person_note_kind_chk CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL', 'LINKEDIN'));

ALTER TABLE app_lm_company_note
    DROP CONSTRAINT app_lm_company_note_kind_chk,
    ADD CONSTRAINT app_lm_company_note_kind_chk CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL', 'LINKEDIN'));
