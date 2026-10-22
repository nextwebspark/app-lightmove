-- Notetaker phase 3 (#952): a recorded call becomes a transcript and one Meeting note.

-- When each audio piece began, by the bot's clock: pieces overlap by a second and may arrive out of order, so the
-- transcript is stitched on this rather than on the upload's number. Null on a captions piece.
ALTER TABLE app_lm_meeting_recording_media
    ADD COLUMN started_at timestamptz,
    -- Passes that could not transcribe this piece; past a few it is given up as a gap, so one bad piece never
    -- holds back the call's note.
    ADD COLUMN transcribe_attempts integer NOT NULL DEFAULT 0;

-- What the transcriber heard in each audio piece, written as the call goes (every few minutes) and kept with its
-- own voice labels, so speakers are named from the captions again whenever the transcript is assembled. Its audio
-- is deleted once this row is stored; the rows go once the transcript is kept whole on the recording.
CREATE TABLE app_lm_meeting_transcript_piece (
    recording_id   uuid        NOT NULL REFERENCES app_lm_meeting_recording (id) ON DELETE CASCADE,
    sequence       integer     NOT NULL,
    started_at     timestamptz NOT NULL,
    lines          jsonb       NOT NULL,
    -- A piece given up after its tries: no lines, and the note says part of the call is missing.
    skipped        boolean     NOT NULL DEFAULT false,
    transcribed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (recording_id, sequence)
);

COMMENT ON TABLE app_lm_meeting_transcript_piece IS
    'A Notetaker run''s transcript so far, one row per transcribed audio piece. Staff-only tenant data, read through its recording.';

ALTER TABLE app_lm_meeting_recording
    -- The position the note was filed under; null for a note about the person.
    ADD COLUMN project_id           uuid        REFERENCES app_lm_project (id) ON DELETE SET NULL,
    ADD COLUMN note_id              uuid        REFERENCES app_lm_person_note (id) ON DELETE SET NULL,
    -- The whole transcript once the note is written: [{at, speaker, text}].
    ADD COLUMN transcript           jsonb,
    -- Set when the bot stopped before the call ended (it crashed or fell silent): the note says it is partial.
    ADD COLUMN recorded_until       timestamptz,
    -- The claim on the run's transcription or processing, so two instances never work it at once.
    ADD COLUMN working_since        timestamptz,
    ADD COLUMN processing_attempts  integer     NOT NULL DEFAULT 0,
    ADD COLUMN processed_at         timestamptz;

CREATE INDEX app_lm_meeting_recording_processing_idx
    ON app_lm_meeting_recording (status, working_since)
    WHERE status IN ('RECORDING', 'PROCESSING');

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED', 'NOTE_RESTORED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED', 'MEETING_RESCHEDULED', 'MEETING_CANCELLED', 'MEETING_RECORDED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED',
                        'ASSESSMENT_REVIEWED'));

ALTER TABLE app_lm_notification DROP CONSTRAINT app_lm_notification_kind_chk;
ALTER TABLE app_lm_notification
    ADD CONSTRAINT app_lm_notification_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'MENTIONED', 'OUTREACH_REPLIED',
                        'OUTREACH_BOUNCED', 'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED', 'MEETING_NOTES_READY'));

ALTER TABLE app_lm_notification_preference DROP CONSTRAINT app_lm_notification_preference_kind_chk;
ALTER TABLE app_lm_notification_preference
    ADD CONSTRAINT app_lm_notification_preference_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE', 'MENTIONED', 'OUTREACH_REPLIED', 'OUTREACH_BOUNCED',
                        'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED', 'MEETING_NOTES', 'DIGEST'));

ALTER TABLE app_lm_usage_event DROP CONSTRAINT app_lm_usage_event_kind_chk;
ALTER TABLE app_lm_usage_event
    ADD CONSTRAINT app_lm_usage_event_kind_chk
        CHECK (kind IN ('PEOPLE_SEARCH_PAGE', 'SOURCING_RUN', 'AI_ENRICH', 'OUTREACH_OPENER', 'ASSISTANT_ASK',
                        'MEETING_MINUTES'));
