-- Meetings with an executive (epic #620, story #628): what the team's connected calendars hold with a
-- person, and a call booked from the drawer.

-- Only an event with one of a person's ledger addresses among its attendees is ever a row, one per
-- matching person; nothing else on a consultant's calendar is kept. The person's, like a note (V96), so
-- every position mapping them reads the same meetings. Cascades from the mailbox it was read from: a
-- disconnected calendar can no longer say when an event moved or was cancelled, so its rows go with it.
CREATE TABLE app_lm_person_meeting (
    id                     uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           uuid          NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id              uuid          NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    mailbox_connection_id  uuid          NOT NULL REFERENCES app_lm_mailbox_connection (id) ON DELETE CASCADE,
    -- The consultant whose calendar holds the event.
    user_id                uuid          NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    provider_event_id      varchar(255)  NOT NULL,
    title                  text,
    starts_at              timestamptz   NOT NULL,
    ends_at                timestamptz   NOT NULL,
    join_url               text,
    conferencing_provider  varchar(64),
    -- Set when the call was booked through Uncava rather than found on a calendar.
    booked_by_user_id      uuid          REFERENCES app_lm_user (id) ON DELETE SET NULL,
    booked_via_link        boolean       NOT NULL DEFAULT false,
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    version                bigint        NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_person_meeting_event_uk UNIQUE (mailbox_connection_id, provider_event_id, person_id)
);

CREATE INDEX app_lm_person_meeting_person_idx ON app_lm_person_meeting (workspace_id, person_id, starts_at);

COMMENT ON TABLE app_lm_person_meeting IS
    'A calendar event with a mapped person. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_person_meeting_touch BEFORE UPDATE ON app_lm_person_meeting
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- Null: the 90-day read of the calendar is still owed — a new connection, a reconnect, or a mailbox
-- connected before this migration.
ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN calendar_synced_at timestamptz;

-- BOOKED: a call was booked with the person, which ends their run as a reply does.
ALTER TABLE app_lm_outreach_enrollment DROP CONSTRAINT app_lm_outreach_enrollment_status_chk;
ALTER TABLE app_lm_outreach_enrollment
    ADD CONSTRAINT app_lm_outreach_enrollment_status_chk
        CHECK (status IN ('SCHEDULED', 'ACTIVE', 'REPLIED', 'BOUNCED', 'STOPPED', 'COMPLETED', 'BOOKED'));

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED', 'EMAIL_SENT', 'EMAIL_REPLIED', 'OUTREACH_STOPPED',
                        'MEETING_BOOKED'));
