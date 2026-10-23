-- The Notetaker (docs/notetaker.md): a Recall.ai bot a consultant sends into a call with an executive, whose
-- transcript becomes one Meeting note on the person. One row per bot.
--
-- The meeting row it was sent for is kept as a bare id, never a foreign key: a calendar's re-read deletes and
-- re-inserts its meetings, and a recording outlives that. starts_at and join_url are the call as it was when the bot
-- went, which is also what keeps one call to one bot whichever teammate's copy of it was pressed.

CREATE TABLE app_lm_meeting_recording (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id       uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    project_id      uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    candidate_id    uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    meeting_id      uuid,
    title           varchar(500),
    starts_at       timestamptz  NOT NULL,
    ends_at         timestamptz  NOT NULL,
    join_url        varchar(2048) NOT NULL,
    requested_by    uuid         NOT NULL REFERENCES app_lm_user (id),
    -- Null only between the claim and Recall's answer, and on a bot Recall refused.
    recall_bot_id   varchar(64)  UNIQUE,
    status          varchar(16)  NOT NULL
        CONSTRAINT app_lm_meeting_recording_status_chk CHECK (status IN
            ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING', 'DONE', 'FAILED', 'CANCELLED')),
    -- Recall's own last status code and sub-code, as it sent them: what a failure is explained from.
    recall_status   varchar(64),
    recall_sub_code varchar(128),
    failure_reason  varchar(64),
    transcript_id   varchar(64),
    -- [{at, speaker, text}], staff-only like the note it became.
    transcript      jsonb,
    note_id         uuid         REFERENCES app_lm_person_note (id) ON DELETE SET NULL,
    media_deleted   boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_meeting_recording IS
    'A Recall.ai Notetaker bot sent into a call with a person, and the transcript it brought back. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_meeting_recording_touch BEFORE UPDATE ON app_lm_meeting_recording
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- One bot per call while one is live: the claim two presses (or two teammates) race on.
CREATE UNIQUE INDEX app_lm_meeting_recording_live_uk ON app_lm_meeting_recording (workspace_id, join_url, starts_at)
    WHERE status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING');

CREATE INDEX app_lm_meeting_recording_person_idx ON app_lm_meeting_recording (workspace_id, person_id);

CREATE INDEX app_lm_meeting_recording_open_idx ON app_lm_meeting_recording (ends_at)
    WHERE status IN ('SCHEDULED', 'JOINING', 'RECORDING', 'PROCESSING');
