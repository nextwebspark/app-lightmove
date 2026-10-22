-- Notetaker 6 (#955): Recall.ai stands behind our own bot. When ours fails before or during a call, the same
-- recording is handed to a Recall bot: recorder becomes RECALL and fallback_reason says why. What our bot captured
-- stays and is stitched before Recall's part; backup_joined_at is when Recall's bot got in, so the note can say where
-- the gap was. A Recall run's note waits for its transcript (backup_transcript_at, set once it is stored).
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN fallback_reason       varchar(32),
    ADD COLUMN backup_joined_at      timestamptz,
    ADD COLUMN backup_transcript_at  timestamptz;

-- Recall's webhook names its bot, which is the run's external id.
CREATE INDEX app_lm_meeting_recording_backup_bot_idx
    ON app_lm_meeting_recording (external_id) WHERE recorder = 'RECALL';

-- The backup is a third party that receives a call's audio, so a workspace sends nothing to it until an admin turns
-- it on (Settings → Integrations → Notetaker). Off by default, unlike the Notetaker's own switch.
ALTER TABLE app_lm_workspace_notetaker
    ADD COLUMN backup_enabled boolean NOT NULL DEFAULT false;
