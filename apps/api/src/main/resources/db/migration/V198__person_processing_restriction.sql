-- Restriction of processing (#920, GDPR Art. 18): what is on file about a person stays readable, and nothing uses
-- it until the restriction is lifted — no AI run reads them, no contact lookup is bought, no email or invite goes,
-- no Notetaker records them, and no door maps them onto a further position. V98's do-not-contact shape: the reason,
-- who and when are kept only while it is set.

ALTER TABLE app_lm_person
    ADD COLUMN processing_restricted         boolean     NOT NULL DEFAULT false,
    ADD COLUMN processing_restricted_reason  text,
    ADD COLUMN processing_restricted_by      uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN processing_restricted_at      timestamptz,
    ADD CONSTRAINT app_lm_person_processing_restricted_chk
        CHECK (processing_restricted
               OR (processing_restricted_reason IS NULL AND processing_restricted_at IS NULL)),
    ADD CONSTRAINT app_lm_person_processing_restricted_set_chk
        CHECK (NOT processing_restricted
               OR (processing_restricted_reason IS NOT NULL AND processing_restricted_at IS NOT NULL)),
    ADD CONSTRAINT app_lm_person_processing_restricted_reason_chk
        CHECK (char_length(processing_restricted_reason) <= 500);

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
                        'ASSESSMENT_REVIEWED', 'UPDATED_FROM_CALL'));

-- A run or a waiting email stops at its next send once the person is restricted.
ALTER TABLE app_lm_outreach_enrollment
    DROP CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk,
    ADD CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
        CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                               'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'BOOKING_LINK_UNAVAILABLE', 'SEND_FAILED',
                               'SEND_UNCERTAIN', 'POSITION_ARCHIVED', 'PROCESSING_RESTRICTED'));

ALTER TABLE app_lm_outreach_message
    DROP CONSTRAINT app_lm_outreach_message_fail_reason_chk,
    ADD CONSTRAINT app_lm_outreach_message_fail_reason_chk
        CHECK (fail_reason IN ('DO_NOT_CONTACT', 'UNMAPPED', 'ADDRESS_REMOVED', 'MAILBOX_INACTIVE', 'MAILBOX_MOVED',
                               'POSITION_ARCHIVED', 'SEND_FAILED', 'SEND_UNCERTAIN', 'SENDER_WITHOUT_ACCESS',
                               'PROCESSING_RESTRICTED'));
