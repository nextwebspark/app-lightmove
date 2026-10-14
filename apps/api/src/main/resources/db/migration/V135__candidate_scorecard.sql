-- A consultant's own assessment of one executive against one position's brief (the drawer's Assessment tab).
-- One per mapping, since it is scored against that brief's competencies. Staff-only: it ranks a person.
--
-- The brief's competency rows have no stable id (each save replaces the list), so a score keeps a snapshot of
-- the criterion it was given against — panel, name, weight — and is matched back on panel and name when read.
CREATE TABLE app_lm_candidate_scorecard (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id    uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    candidate_id  uuid        NOT NULL REFERENCES app_lm_project_candidate (id) ON DELETE CASCADE,
    status        varchar(16) NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT app_lm_candidate_scorecard_status_chk CHECK (status IN ('DRAFT', 'REVIEWED')),
    scores        jsonb       NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT app_lm_candidate_scorecard_scores_chk CHECK (jsonb_typeof(scores) = 'array'),
    report        jsonb       NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT app_lm_candidate_scorecard_report_chk CHECK (jsonb_typeof(report) = 'object'),
    updated_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    reviewed_by   uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    reviewed_at   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_candidate_scorecard_candidate_uk UNIQUE (candidate_id),
    CONSTRAINT app_lm_candidate_scorecard_review_chk
        CHECK ((status = 'REVIEWED') = (reviewed_at IS NOT NULL))
);

COMMENT ON TABLE app_lm_candidate_scorecard IS
    'A consultant''s scored assessment of one mapped executive against the position''s brief. Tenant data, '
    'staff-only: every read filters by workspace_id and never rides CandidateResponse.';

CREATE TRIGGER app_lm_candidate_scorecard_touch BEFORE UPDATE ON app_lm_candidate_scorecard
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE INDEX app_lm_candidate_scorecard_project_idx ON app_lm_candidate_scorecard (project_id);

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
                        'NEXT_ACTION_SET', 'CLIENT_VISIBILITY_CHANGED',
                        'TASK_CREATED', 'TASK_COMPLETED', 'TASK_REOPENED', 'TASK_REASSIGNED', 'TASK_CANCELLED',
                        'ASSESSMENT_REVIEWED'));
