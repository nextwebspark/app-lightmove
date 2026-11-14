-- Outreach opt-out (#924): every outreach email carries a footer naming the firm, where the details came from, and a
-- link to stop emails, which is also the List-Unsubscribe header's one-click target. Each email's link is a privacy
-- link of its own (V218's table), valid for a year; used_at is when the person took it.

ALTER TABLE app_lm_person_privacy_link DROP CONSTRAINT app_lm_person_privacy_link_purpose_chk;
ALTER TABLE app_lm_person_privacy_link
    ADD CONSTRAINT app_lm_person_privacy_link_purpose_chk CHECK (purpose IN ('NOTICE', 'OPT_OUT')),
    ADD COLUMN used_at timestamptz;

-- An opt-out ends every live run on the person and fails every email still waiting to go.
ALTER TABLE app_lm_outreach_enrollment
    DROP CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk,
    ADD CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
        CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                               'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'BOOKING_LINK_UNAVAILABLE', 'SEND_FAILED',
                               'SEND_UNCERTAIN', 'POSITION_ARCHIVED', 'PROCESSING_RESTRICTED',
                               'WORKSPACE_DELETED', 'OPTED_OUT'));

ALTER TABLE app_lm_outreach_message
    DROP CONSTRAINT app_lm_outreach_message_fail_reason_chk,
    ADD CONSTRAINT app_lm_outreach_message_fail_reason_chk
        CHECK (fail_reason IN ('DO_NOT_CONTACT', 'UNMAPPED', 'ADDRESS_REMOVED', 'MAILBOX_INACTIVE', 'MAILBOX_MOVED',
                               'POSITION_ARCHIVED', 'SEND_FAILED', 'SEND_UNCERTAIN', 'SENDER_WITHOUT_ACCESS',
                               'PROCESSING_RESTRICTED', 'OPTED_OUT'));

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
                        'PRIVACY_NOTICE_FAILED', 'PRIVACY_OBJECTED', 'PRIVACY_REQUEST_RECEIVED',
                        'PRIVACY_OPTED_OUT'));

-- The person's owner, else whoever sent the email they opted out of, hears of it.
ALTER TABLE app_lm_notification DROP CONSTRAINT app_lm_notification_kind_chk;
ALTER TABLE app_lm_notification
    ADD CONSTRAINT app_lm_notification_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'MENTIONED', 'OUTREACH_REPLIED',
                        'OUTREACH_BOUNCED', 'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED',
                        'COPY_REQUESTED', 'DELETION_REQUESTED', 'PERSON_OBJECTED', 'CANDIDATE_OPTED_OUT'));
