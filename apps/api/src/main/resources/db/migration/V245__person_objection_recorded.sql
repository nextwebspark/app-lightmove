-- A colleague records that a person objected when contacted (B16, #1489): the same stop as the privacy page's
-- objection and an email's stop link, written as its own timeline line because a colleague, not the person, acted.
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
                        'PRIVACY_OPTED_OUT', 'OBJECTION_RECORDED',
                        'CONSENT_REQUESTED', 'CONSENT_REQUEST_FAILED', 'CONSENT_ANSWERED', 'CONSENT_EXPIRED'));

-- Which door a standing do not contact is the person's objection through; null for the team's own flag. The reason
-- beside it is on-screen copy, and with data protection off a consultant's free text, so it never says which.
ALTER TABLE app_lm_person
    ADD COLUMN do_not_contact_source text,
    ADD CONSTRAINT app_lm_person_do_not_contact_source_chk
        CHECK (do_not_contact_source IS NULL
               OR (do_not_contact
                   AND do_not_contact_source IN ('OBJECTED_ON_PRIVACY_PAGE', 'OPTED_OUT_BY_EMAIL',
                                                 'RECORDED_BY_TEAM')));

-- Before the column the person's own doors wrote these reasons with nobody as the setter; a colleague typing the
-- same words set it as themselves, so it stays theirs.
UPDATE app_lm_person
SET do_not_contact_source = CASE do_not_contact_reason
        WHEN 'Objected through their privacy page' THEN 'OBJECTED_ON_PRIVACY_PAGE'
        WHEN 'Opted out by email' THEN 'OPTED_OUT_BY_EMAIL'
    END
WHERE do_not_contact
  AND do_not_contact_set_by IS NULL
  AND do_not_contact_reason IN ('Objected through their privacy page', 'Opted out by email');
UPDATE app_lm_person
SET do_not_contact_source = 'RECORDED_BY_TEAM'
WHERE do_not_contact
  AND do_not_contact_reason = 'Objected when contacted, recorded by the team';
