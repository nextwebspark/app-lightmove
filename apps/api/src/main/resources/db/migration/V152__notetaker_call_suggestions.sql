-- "Suggest changes" on a recorded call: what the AI read off the transcript for the person's record, kept on the
-- recording until each one is applied or skipped. The quotes are the executive's words, so the column goes with the
-- transcript wherever lightmove.recall.transcript-retention clears it; unset, both are kept.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN suggestions jsonb,
    ADD COLUMN suggested_at timestamptz,
    ADD COLUMN suggesting_since timestamptz;

COMMENT ON COLUMN app_lm_meeting_recording.suggestions IS
    'Changes the call transcript suggests for the person: [{id, kind, value, said, at, status, ...}]. Null until '
    'someone presses Suggest changes; an empty array when the call stated nothing new. Staff-only.';
COMMENT ON COLUMN app_lm_meeting_recording.suggesting_since IS
    'Claim held while one press asks the model, so two presses make one call; older than its deadline is stale.';

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'TRIAGE_STATUS_CHANGED',
                        'PROFILE_EDITED', 'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED', 'NOTE_RESTORED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED', 'MEETING_RESCHEDULED', 'MEETING_CANCELLED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED',
                        'ASSESSMENT_REVIEWED', 'UPDATED_FROM_CALL'));
