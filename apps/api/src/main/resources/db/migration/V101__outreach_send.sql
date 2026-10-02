-- Outreach sends (epic #620, story #624): the dispatcher takes SCHEDULED enrollments out through the
-- sender's own mailbox, threads the follow-ups under the first email, and a reply, a bounce, a Stop or
-- a failed re-check ends the run.

-- The sending window is the sender's working day, so it is read in their own zone. The GCC's working
-- week is the UAE's by default; a consultant elsewhere changes it once.
ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN time_zone varchar(64) NOT NULL DEFAULT 'Asia/Dubai';

-- sending_since is the dispatcher's claim. It is set and committed before the mail service is called,
-- so a second instance never picks the row up, and a claim nobody released is a send whose outcome is
-- unknown: it is stopped as SEND_UNCERTAIN, never sent again, because a second copy of an approach to
-- an executive is worse than a missing one.
ALTER TABLE app_lm_outreach_enrollment
    ADD COLUMN thread_id        varchar(255),
    ADD COLUMN last_message_id  varchar(255),
    ADD COLUMN last_sent_at     timestamptz,
    ADD COLUMN replied_at       timestamptz,
    ADD COLUMN stopped_at       timestamptz,
    ADD COLUMN sending_since    timestamptz,
    ADD COLUMN stop_reason      varchar(24)
        CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
            CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                                   'MAILBOX_INACTIVE', 'SEND_FAILED', 'SEND_UNCERTAIN'));

CREATE INDEX app_lm_outreach_enrollment_due_idx ON app_lm_outreach_enrollment (next_send_at)
    WHERE status IN ('SCHEDULED', 'ACTIVE');

-- A reply webhook names the grant and the thread; the thread is ours to look up.
CREATE INDEX app_lm_outreach_enrollment_thread_idx ON app_lm_outreach_enrollment (thread_id)
    WHERE thread_id IS NOT NULL;

-- Every email that went, as it went. Staff-only: a client seat reads none of outreach. A reply is never
-- a row here — its content is the consultant's inbox's, not ours.
CREATE TABLE app_lm_outreach_message (
    id                   uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id         uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    enrollment_id        uuid         NOT NULL REFERENCES app_lm_outreach_enrollment (id) ON DELETE CASCADE,
    step                 integer      NOT NULL
        CONSTRAINT app_lm_outreach_message_step_chk CHECK (step BETWEEN 0 AND 2),
    sender_user_id       uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    provider_message_id  varchar(255) NOT NULL,
    thread_id            varchar(255),
    subject              text         NOT NULL,
    body                 text         NOT NULL,
    sent_at              timestamptz  NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    version              bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_outreach_message_step_uk UNIQUE (enrollment_id, step)
);

-- The daily cap counts one sender's sends since their local midnight.
CREATE INDEX app_lm_outreach_message_sender_idx ON app_lm_outreach_message (workspace_id, sender_user_id, sent_at);

COMMENT ON TABLE app_lm_outreach_message IS
    'An outreach email as sent. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_outreach_message_touch BEFORE UPDATE ON app_lm_outreach_message
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED'));
