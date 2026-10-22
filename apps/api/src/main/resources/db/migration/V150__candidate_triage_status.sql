-- An executive stands at a stage of their own (#903): moving or declining one person at a company no longer takes
-- every colleague with them. A company's own move carries the people still where it stood; a person moved alone keeps
-- their stage until they are moved again or remapped to another company, whose stage they then take. Every move is a
-- TRIAGE_STATUS_CHANGED line on the person's timeline.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN triage_status varchar(16) NOT NULL DEFAULT 'IN_UNIVERSE'
        CONSTRAINT app_lm_project_candidate_triage_status_chk
        CHECK (triage_status IN ('IN_UNIVERSE', 'SHORTLISTED', 'DECLINED'));

COMMENT ON COLUMN app_lm_project_candidate.triage_status IS
    'The Companies stage this executive is listed under (In universe / Shortlisted / Declined); their company''s at filing.';

UPDATE app_lm_project_candidate c
   SET triage_status = t.status
  FROM app_lm_project_triage_company t
 WHERE t.id = c.triage_company_id
   AND t.status <> 'IN_UNIVERSE';

-- A stage page reads its people by stage, and its companies by "the company is here, or one of its people is".
CREATE INDEX app_lm_project_candidate_triage_status_idx
    ON app_lm_project_candidate (project_id, triage_status, triage_company_id);

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
                        'ASSESSMENT_REVIEWED'));
