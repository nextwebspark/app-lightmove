-- A privacy notice the mail provider refused (#923): the person is put back to how they were last told, so the Art. 14
-- record never claims a notice reached someone it did not. The link row stays as the history of the attempt, marked
-- failed, and still counts against the workspace's daily cap: a refused send spends the sender's reputation too.

ALTER TABLE app_lm_person_privacy_link ADD COLUMN failed_at timestamptz;

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'TRIAGE_STATUS_CHANGED',
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
                        'PRIVACY_NOTICE_FAILED', 'PRIVACY_OBJECTED', 'PRIVACY_REQUEST_RECEIVED'));
