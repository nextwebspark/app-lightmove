-- The Notetaker on demand: Start now sends a bot at once whenever the consultant is ready, and End & file notes takes
-- it out of a call that finished early, so the notes need not wait for the booked end.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN join_at timestamptz,
    ADD COLUMN recording_started_at timestamptz,
    ADD COLUMN end_requested_at timestamptz,
    ADD COLUMN end_requested_by uuid REFERENCES app_lm_user (id),
    ADD CONSTRAINT app_lm_meeting_recording_end_requested_chk
        CHECK ((end_requested_at IS NULL) = (end_requested_by IS NULL));

COMMENT ON COLUMN app_lm_meeting_recording.join_at IS
    'When Recall was asked to bring the bot in; null for a bot sent at once.';
COMMENT ON COLUMN app_lm_meeting_recording.recording_started_at IS
    'When Recall first reported the bot recording: the call clock counts from here, not from the booked start.';
COMMENT ON COLUMN app_lm_meeting_recording.end_requested_at IS
    'End & file notes pressed: the bot was asked to leave, and the note follows its call_ended.';

UPDATE app_lm_meeting_recording
SET join_at = starts_at - interval '1 minute'
WHERE status = 'SCHEDULED' AND recall_bot_id IS NOT NULL AND created_at < starts_at - interval '2 minutes';
