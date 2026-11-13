-- Candidate privacy notices (#923, #1267 phase 1, GDPR Art. 14): every person a workspace holds was sourced, never
-- asked, so the firm (the controller) owes them a notice within a month and a recorded lawful basis.
--
-- The firm's own details live in one row per workspace: named as controller in every notice, the contact replies go
-- to, its notice's link, and the basis recorded for anyone with none of their own. No row means nothing is set up,
-- and no notice can be sent until the first three are filled.

CREATE TABLE app_lm_workspace_candidate_privacy (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    controller_name        varchar(200),
    privacy_contact_email  varchar(320),
    notice_url             varchar(2048),
    default_lawful_basis   varchar(24)  NOT NULL DEFAULT 'LEGITIMATE_INTEREST'
        CONSTRAINT app_lm_workspace_candidate_privacy_basis_chk
            CHECK (default_lawful_basis IN ('LEGITIMATE_INTEREST', 'CONSENT', 'CONTRACT')),
    updated_by             uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_workspace_candidate_privacy_workspace_uk UNIQUE (workspace_id)
);

COMMENT ON TABLE app_lm_workspace_candidate_privacy IS
    'The firm as controller of its candidates (#923): its legal name, privacy contact, notice link and default lawful basis. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_candidate_privacy_touch BEFORE UPDATE ON app_lm_workspace_candidate_privacy
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_workspace_candidate_privacy ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_candidate_privacy FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_candidate_privacy
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- A person's own basis, when someone recorded one (null follows the workspace's default), and when and how they were
-- last told. Who set the basis and when are kept only while one is set, as do not contact's are.
ALTER TABLE app_lm_person
    ADD COLUMN lawful_basis           varchar(24)
        CONSTRAINT app_lm_person_lawful_basis_chk
            CHECK (lawful_basis IN ('LEGITIMATE_INTEREST', 'CONSENT', 'CONTRACT')),
    ADD COLUMN lawful_basis_set_by    uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN lawful_basis_set_at    timestamptz,
    ADD COLUMN privacy_notified_at    timestamptz,
    ADD COLUMN privacy_notice_channel varchar(16)
        CONSTRAINT app_lm_person_privacy_notice_channel_chk
            CHECK (privacy_notice_channel IN ('PRIVACY_EMAIL', 'OUTREACH_EMAIL', 'MARKED_BY_HAND')),
    ADD CONSTRAINT app_lm_person_lawful_basis_set_chk
        CHECK ((lawful_basis IS NULL) = (lawful_basis_set_at IS NULL)),
    ADD CONSTRAINT app_lm_person_privacy_notice_chk
        CHECK ((privacy_notified_at IS NULL) = (privacy_notice_channel IS NULL));

-- The Not notified quick view: held over a month and never told.
CREATE INDEX app_lm_person_not_notified_idx ON app_lm_person (workspace_id, created_at)
    WHERE privacy_notified_at IS NULL;

-- One row per notice sent: the history the drawer and a subject access export read, and the key to the public page
-- it links to. The token is stored only as its SHA-256; the page answers for 30 days.
CREATE TABLE app_lm_person_privacy_link (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id       uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    token_hash      varchar(64)  NOT NULL,
    purpose         varchar(16)  NOT NULL
        CONSTRAINT app_lm_person_privacy_link_purpose_chk CHECK (purpose IN ('NOTICE')),
    sent_by         uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    expires_at      timestamptz  NOT NULL,
    last_opened_at  timestamptz,
    CONSTRAINT app_lm_person_privacy_link_token_uk UNIQUE (token_hash)
);

COMMENT ON TABLE app_lm_person_privacy_link IS
    'A privacy notice sent to a person (#923), and the hashed key to the public page it links to. Deleted with the person.';

CREATE INDEX app_lm_person_privacy_link_person_idx ON app_lm_person_privacy_link (person_id, created_at DESC);
CREATE INDEX app_lm_person_privacy_link_workspace_day_idx ON app_lm_person_privacy_link (workspace_id, created_at);

ALTER TABLE app_lm_person_privacy_link ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_person_privacy_link FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_person_privacy_link
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The public page arrives with a token and no workspace (V189's idiom): which workspace holds it, never the row.
CREATE FUNCTION app_lm_resolve_person_privacy_link(p_token_hash text)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY SELECT l.id, l.workspace_id FROM app_lm_person_privacy_link l WHERE l.token_hash = p_token_hash;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION app_lm_resolve_person_privacy_link(text) FROM PUBLIC;

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
                        'PRIVACY_OBJECTED', 'PRIVACY_REQUEST_RECEIVED'));

-- A copy or a deletion asked for from the public page, and an objection made there, reach the workspace's admins.
ALTER TABLE app_lm_notification DROP CONSTRAINT app_lm_notification_kind_chk;
ALTER TABLE app_lm_notification
    ADD CONSTRAINT app_lm_notification_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'MENTIONED', 'OUTREACH_REPLIED',
                        'OUTREACH_BOUNCED', 'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED',
                        'COPY_REQUESTED', 'DELETION_REQUESTED', 'PERSON_OBJECTED'));

ALTER TABLE app_lm_notification_preference DROP CONSTRAINT app_lm_notification_preference_kind_chk;
ALTER TABLE app_lm_notification_preference
    ADD CONSTRAINT app_lm_notification_preference_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE', 'MENTIONED', 'OUTREACH_REPLIED', 'OUTREACH_BOUNCED',
                        'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED', 'PRIVACY_REQUEST', 'DIGEST'));
