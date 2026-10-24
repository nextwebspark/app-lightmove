-- A recorded call's record for the AI: the fuller account the summariser writes beside the Meeting note, read by every
-- later AI run on that position in place of a transcript cut down to fit. Never shown to anyone. It quotes the
-- executive, so it goes with the transcript wherever lightmove.recall.transcript-retention clears it.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN call_record text,
    ADD COLUMN call_record_version smallint,
    ADD CONSTRAINT app_lm_meeting_recording_call_record_chk
        CHECK ((call_record IS NULL) = (call_record_version IS NULL));

COMMENT ON COLUMN app_lm_meeting_recording.call_record IS
    'The call as the AI reads it (Markdown): account, facts stated, evidence per competency with quotes checked '
    'against the transcript, concerns, open questions, next steps. Written by code from the summary. Staff-only.';
COMMENT ON COLUMN app_lm_meeting_recording.call_record_version IS
    'The summariser revision that wrote the record, so a better prompt can rewrite older ones.';
