-- Why this mandate's candidate left the process: one of the workspace's reasons, a note where the reason asks for one,
-- and who gave it when. Held only while the candidate's status is out of process; a move back in clears it. A row
-- moved out before V228 holds none and reads "Not given" until someone fills it in. Staff-only, like the reasons.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN exit_reason_id     uuid REFERENCES app_lm_workspace_exit_reason (id) ON DELETE SET NULL,
    ADD COLUMN exit_note          varchar(500),
    ADD COLUMN exit_reason_set_at timestamptz,
    ADD COLUMN exit_reason_set_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT app_lm_project_candidate_exit_note_chk
        CHECK (btrim(exit_note) <> '');

CREATE INDEX app_lm_project_candidate_exit_reason_idx
    ON app_lm_project_candidate (exit_reason_id) WHERE exit_reason_id IS NOT NULL;

-- A different reason, or note, for someone already out of process is a timeline line of its own.
ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'EXIT_REASON_CHANGED',
                        'TRIAGE_STATUS_CHANGED',
                        'PROFILE_EDITED', 'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED', 'NOTE_RESTORED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'PROCESSING_RESTRICTED', 'PROCESSING_RESTRICTION_LIFTED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED', 'MEETING_RESCHEDULED', 'MEETING_CANCELLED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED',
                        'ASSESSMENT_REVIEWED', 'UPDATED_FROM_CALL',
                        'LAWFUL_BASIS_CHANGED', 'PRIVACY_NOTICE_SENT', 'PRIVACY_NOTICE_MARKED',
                        'PRIVACY_NOTICE_FAILED', 'PRIVACY_OBJECTED', 'PRIVACY_REQUEST_RECEIVED',
                        'PRIVACY_OPTED_OUT',
                        'CONSENT_REQUESTED', 'CONSENT_REQUEST_FAILED', 'CONSENT_ANSWERED', 'CONSENT_EXPIRED'));
