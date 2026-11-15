-- Consent requests (#1267 phases 2 and 3): a firm in consent mode asks a person, per purpose, whether it may keep
-- their details on file, share their profile with a client for one position, or send them market updates. Silence is
-- never consent: a request nobody answers closes after 30 days and changes nothing.

ALTER TABLE app_lm_workspace_candidate_privacy
    ADD COLUMN privacy_mode              varchar(16) NOT NULL DEFAULT 'NOTICES'
        CONSTRAINT app_lm_workspace_candidate_privacy_mode_chk CHECK (privacy_mode IN ('NOTICES', 'CONSENT')),
    ADD COLUMN consent_keep_on_file      boolean     NOT NULL DEFAULT true,
    ADD COLUMN keep_on_file_months       smallint    NOT NULL DEFAULT 24
        CONSTRAINT app_lm_workspace_candidate_privacy_keep_months_chk CHECK (keep_on_file_months IN (12, 24, 36)),
    ADD COLUMN consent_share_with_client boolean     NOT NULL DEFAULT true,
    ADD COLUMN consent_marketing         boolean     NOT NULL DEFAULT false,
    ADD COLUMN consent_renewal_days      smallint    NOT NULL DEFAULT 30
        CONSTRAINT app_lm_workspace_candidate_privacy_renewal_chk CHECK (consent_renewal_days BETWEEN 0 AND 90);

-- A consent request links to the page the person answers on; a confirmation links to where they can withdraw.
ALTER TABLE app_lm_person_privacy_link DROP CONSTRAINT app_lm_person_privacy_link_purpose_chk;
ALTER TABLE app_lm_person_privacy_link
    ADD CONSTRAINT app_lm_person_privacy_link_purpose_chk
        CHECK (purpose IN ('NOTICE', 'OPT_OUT', 'CONSENT', 'MANAGE'));

-- Where each purpose stands for a person: no row is never asked. A share is one position's, the others the person's.
-- request_expires_at is set while a request is open; on a GIVEN row it is a renewal waiting for an answer.
CREATE TABLE app_lm_person_consent (
    id                  uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id        uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id           uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    purpose             varchar(24)  NOT NULL
        CONSTRAINT app_lm_person_consent_purpose_chk
            CHECK (purpose IN ('KEEP_ON_FILE', 'SHARE_WITH_CLIENT', 'MARKETING')),
    project_id          uuid         REFERENCES app_lm_project (id) ON DELETE CASCADE,
    status              varchar(16)  NOT NULL
        CONSTRAINT app_lm_person_consent_status_chk
            CHECK (status IN ('REQUESTED', 'GIVEN', 'DECLINED', 'WITHDRAWN', 'EXPIRED')),
    requested_at        timestamptz,
    requested_by        uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    request_link_id     uuid         REFERENCES app_lm_person_privacy_link (id) ON DELETE SET NULL,
    request_expires_at  timestamptz,
    answered_at         timestamptz,
    answered_via        varchar(16)
        CONSTRAINT app_lm_person_consent_via_chk CHECK (answered_via IN ('PAGE', 'BY_HAND')),
    valid_until         timestamptz,
    client_named        boolean      NOT NULL DEFAULT false,
    wording_version     varchar(32),
    wording             text,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_person_consent_share_position_chk
        CHECK ((purpose = 'SHARE_WITH_CLIENT') = (project_id IS NOT NULL)),
    CONSTRAINT app_lm_person_consent_answer_chk CHECK ((answered_at IS NULL) = (answered_via IS NULL)),
    CONSTRAINT app_lm_person_consent_uk UNIQUE NULLS NOT DISTINCT (person_id, purpose, project_id)
);

COMMENT ON TABLE app_lm_person_consent IS
    'A person''s answer per purpose (#1267): keep on file, share with a client for one position, market updates. Deleted with the person.';

CREATE INDEX app_lm_person_consent_open_idx ON app_lm_person_consent (workspace_id, request_expires_at)
    WHERE request_expires_at IS NOT NULL;
CREATE INDEX app_lm_person_consent_given_idx ON app_lm_person_consent (workspace_id, valid_until)
    WHERE status = 'GIVEN';
CREATE INDEX app_lm_person_consent_project_idx ON app_lm_person_consent (project_id) WHERE project_id IS NOT NULL;
CREATE INDEX app_lm_person_consent_link_idx ON app_lm_person_consent (request_link_id)
    WHERE request_link_id IS NOT NULL;

CREATE TRIGGER app_lm_person_consent_touch BEFORE UPDATE ON app_lm_person_consent
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_person_consent ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_person_consent FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_person_consent
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- Who was asked what, when, under which wording, and what they answered. Never updated; deleted with the person or
-- the workspace. The link and the actor carry no foreign key: a SET NULL would be an update.
CREATE TABLE app_lm_person_consent_event (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id        uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    consent_id       uuid         NOT NULL REFERENCES app_lm_person_consent (id) ON DELETE CASCADE,
    action           varchar(16)  NOT NULL
        CONSTRAINT app_lm_person_consent_event_action_chk
            CHECK (action IN ('REQUESTED', 'ANSWERED', 'RECORDED', 'EXPIRED')),
    status           varchar(16)  NOT NULL
        CONSTRAINT app_lm_person_consent_event_status_chk
            CHECK (status IN ('REQUESTED', 'GIVEN', 'DECLINED', 'WITHDRAWN', 'EXPIRED')),
    via              varchar(16)  NOT NULL
        CONSTRAINT app_lm_person_consent_event_via_chk CHECK (via IN ('STAFF', 'PAGE', 'BY_HAND', 'SYSTEM')),
    link_id          uuid,
    actor_user_id    uuid,
    wording_version  varchar(32),
    wording          text,
    occurred_at      timestamptz  NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_person_consent_event IS
    'The append-only history of a person''s consents (#1267): each request, answer, record by hand and expiry. Deleted with the person.';

CREATE INDEX app_lm_person_consent_event_person_idx ON app_lm_person_consent_event (person_id, occurred_at);
CREATE INDEX app_lm_person_consent_event_consent_idx ON app_lm_person_consent_event (consent_id);

CREATE FUNCTION app_lm_person_consent_event_is_append_only() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'app_lm_person_consent_event is append-only';
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER app_lm_person_consent_event_append_only BEFORE UPDATE ON app_lm_person_consent_event
    FOR EACH ROW EXECUTE FUNCTION app_lm_person_consent_event_is_append_only();

ALTER TABLE app_lm_person_consent_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_person_consent_event FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_person_consent_event
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The daily consent scan (V191's idiom): a request past its 30 days, a consent past its date, or a keep-on-file
-- consent near enough its date to ask again. Wider than the scan's own claims, never narrower.
CREATE FUNCTION app_lm_workspaces_with_consents_due(p_now timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT c.workspace_id FROM app_lm_person_consent c
         WHERE c.request_expires_at <= p_now
            OR (c.status = 'GIVEN' AND c.valid_until <= p_now + interval '90 days');
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION app_lm_workspaces_with_consents_due(timestamptz) FROM PUBLIC;

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
                        'PRIVACY_OPTED_OUT',
                        'CONSENT_REQUESTED', 'CONSENT_REQUEST_FAILED', 'CONSENT_ANSWERED', 'CONSENT_EXPIRED'));

-- Whoever asked, else the person's owner, hears an answer given on the page.
ALTER TABLE app_lm_notification DROP CONSTRAINT app_lm_notification_kind_chk;
ALTER TABLE app_lm_notification
    ADD CONSTRAINT app_lm_notification_kind_chk
        CHECK (kind IN ('TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE', 'MENTIONED', 'OUTREACH_REPLIED',
                        'OUTREACH_BOUNCED', 'CALL_BOOKED', 'ADDED_TO_TEAM', 'TASKS_MOVED',
                        'COPY_REQUESTED', 'DELETION_REQUESTED', 'PERSON_OBJECTED', 'CANDIDATE_OPTED_OUT',
                        'CONSENT_ANSWERED'));
