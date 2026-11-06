-- A meeting's notes may name the Notetaker recording they are about when the calendar no longer holds the meeting it
-- was sent to, so a researcher's own notes sit beside the Notetaker's on that call too. The id is kept bare for V185's
-- reason, and a note names a calendar meeting or a recording, never both.
ALTER TABLE app_lm_person_note
    ADD COLUMN recording_id uuid,
    ADD CONSTRAINT app_lm_person_note_recording_kind_chk CHECK (recording_id IS NULL OR kind = 'MEETING'),
    ADD CONSTRAINT app_lm_person_note_one_meeting_chk CHECK (meeting_id IS NULL OR recording_id IS NULL);
