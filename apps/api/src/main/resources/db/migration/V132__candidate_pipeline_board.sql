-- The Pipeline page's three facts about a candidate on one position: the manual order within its stage,
-- whether a client seat on the position sees them at all, and what happens next.
--
-- Only adds, and every existing executive stays visible, so a client seat reads exactly what it read
-- before until someone hides one.

ALTER TABLE app_lm_project_candidate
    -- Fractional, so a drag between two rows writes only the row moved. Null until anyone drags it.
    ADD COLUMN pipeline_rank       numeric,
    ADD COLUMN client_visible      boolean     NOT NULL DEFAULT true,
    ADD COLUMN next_action         text,
    ADD COLUMN next_action_due     timestamptz,
    ADD COLUMN next_action_set_by  uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD CONSTRAINT app_lm_project_candidate_next_action_chk
        CHECK (char_length(next_action) <= 200),
    ADD CONSTRAINT app_lm_project_candidate_next_action_due_chk
        CHECK (next_action IS NOT NULL OR next_action_due IS NULL);

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED',
                        'DOCUMENT_ADDED', 'DOCUMENT_VERSION_ADDED', 'DOCUMENT_REMOVED',
                        'DOCUMENT_VERSION_REMOVED',
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED'));
