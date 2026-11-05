-- A logged call, LinkedIn message or meeting says when it happened, which is not always when it was typed in, may
-- carry a short title, and a meeting's notes may name the calendar meeting they are about. The meeting id is kept
-- bare, never a foreign key: a calendar re-read replaces meeting rows (V151 keeps the recording's the same way).
ALTER TABLE app_lm_person_note
    ADD COLUMN occurred_at timestamptz,
    ADD COLUMN title       varchar(200),
    ADD COLUMN meeting_id  uuid,
    ADD CONSTRAINT app_lm_person_note_meeting_kind_chk CHECK (meeting_id IS NULL OR kind = 'MEETING'),
    ADD CONSTRAINT app_lm_person_note_occurrence_kind_chk CHECK (occurred_at IS NULL OR kind <> 'GENERAL');
