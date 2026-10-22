-- Uncava Notetaker (epic #949, phase 1 #950): our own headless-browser bot joins a kept meeting's call, Recall only
-- when it fails. A call is joined only while three switches agree — the workspace's, the consultant's and the
-- meeting's — and each is on until someone turns it off, so no row below means on.

CREATE TABLE app_lm_workspace_notetaker (
    workspace_id  uuid        PRIMARY KEY REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    enabled       boolean     NOT NULL,
    updated_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    updated_at    timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_workspace_notetaker IS
    'A workspace''s Notetaker switch; no row is on. Tenant data.';

CREATE TABLE app_lm_notetaker_user_preference (
    user_id       uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    enabled       boolean     NOT NULL,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, workspace_id)
);

COMMENT ON TABLE app_lm_notetaker_user_preference IS
    'Whether the Notetaker joins one consultant''s meetings in one workspace; no row is on. Tenant data.';

-- Keyed on the calendar event, never on app_lm_person_meeting's id: a sync rewrites those rows, and a meeting
-- moved to another day is still the meeting somebody said no to.
CREATE TABLE app_lm_notetaker_meeting_opt_out (
    mailbox_connection_id  uuid         NOT NULL REFERENCES app_lm_mailbox_connection (id) ON DELETE CASCADE,
    provider_event_id      varchar(255) NOT NULL,
    workspace_id           uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    opted_out_by           uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (mailbox_connection_id, provider_event_id)
);

COMMENT ON TABLE app_lm_notetaker_meeting_opt_out IS
    'A calendar event the Notetaker must not join. Tenant data: every read filters by workspace_id.';

-- One bot per call. Two teammates' calendars hold one call twice, so the call is its join link and start
-- (call_key, a SHA-256 of both), and inserting that key is the claim two instances race on.
CREATE TABLE app_lm_meeting_recording (
    id                     uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id           uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    call_key               varchar(64)  NOT NULL,
    -- The consultant whose calendar the bot was sent from.
    user_id                uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    person_id              uuid         REFERENCES app_lm_person (id) ON DELETE SET NULL,
    project_id             uuid         REFERENCES app_lm_project (id) ON DELETE SET NULL,
    mailbox_connection_id  uuid         REFERENCES app_lm_mailbox_connection (id) ON DELETE SET NULL,
    provider_event_id      varchar(255) NOT NULL,
    platform               varchar(16)  NOT NULL
        CONSTRAINT app_lm_meeting_recording_platform_chk
            CHECK (platform IN ('GOOGLE_MEET', 'MICROSOFT_TEAMS', 'ZOOM')),
    join_url               text         NOT NULL,
    title                  text,
    starts_at              timestamptz  NOT NULL,
    ends_at                timestamptz  NOT NULL,
    recorder               varchar(8)   NOT NULL DEFAULT 'OWN'
        CONSTRAINT app_lm_meeting_recording_recorder_chk CHECK (recorder IN ('OWN', 'RECALL')),
    status                 varchar(20)  NOT NULL
        CONSTRAINT app_lm_meeting_recording_status_chk
            CHECK (status IN ('LAUNCHING', 'WAITING_ROOM', 'RECORDING', 'REMOVE_REQUESTED', 'PROCESSING', 'DONE',
                              'FAILED', 'REMOVED', 'SKIPPED')),
    end_reason             varchar(32),
    external_id            varchar(255),
    -- SHA-256 of the run token the bot calls back with; cleared once the run can no longer change.
    run_token_hash         varchar(64),
    launched_at            timestamptz,
    last_heartbeat_at      timestamptz,
    joined_at              timestamptz,
    left_at                timestamptz,
    remove_requested_by    uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    remove_requested_at    timestamptz,
    media                  jsonb,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_meeting_recording_call_uk UNIQUE (workspace_id, call_key),
    CONSTRAINT app_lm_meeting_recording_ended_chk
        CHECK ((status IN ('FAILED', 'SKIPPED')) = (end_reason IS NOT NULL))
);

COMMENT ON TABLE app_lm_meeting_recording IS
    'One Notetaker bot on one call. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_meeting_recording_touch BEFORE UPDATE ON app_lm_meeting_recording
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_meeting_recording_token_uk
    ON app_lm_meeting_recording (run_token_hash) WHERE run_token_hash IS NOT NULL;

-- The sweeper's scan: runs a bot may still be in.
CREATE INDEX app_lm_meeting_recording_live_idx
    ON app_lm_meeting_recording (status, last_heartbeat_at)
    WHERE status IN ('LAUNCHING', 'WAITING_ROOM', 'RECORDING', 'REMOVE_REQUESTED');

CREATE INDEX app_lm_meeting_recording_person_idx
    ON app_lm_meeting_recording (workspace_id, person_id, starts_at) WHERE person_id IS NOT NULL;

-- The scheduler's scan over every workspace's calendars: meetings about to start with a link to join.
CREATE INDEX app_lm_person_meeting_starts_idx
    ON app_lm_person_meeting (starts_at) WHERE join_url IS NOT NULL;
