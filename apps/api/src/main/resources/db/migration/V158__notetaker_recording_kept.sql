-- The call's recording stays at Recall for a week after the call, so its sender can download it; Recall deletes it
-- itself when its timed retention lapses.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN media_expires_at timestamptz;

COMMENT ON COLUMN app_lm_meeting_recording.media_expires_at IS
    'When Recall drops the call''s recording (its timed retention from the call''s end); null until the call has ended.';
