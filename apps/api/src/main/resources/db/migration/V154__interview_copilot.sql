-- The interview copilot (docs/notetaker.md): Prepare questions before a call with an executive, and a live copilot
-- during it that follows the Notetaker's transcript as Recall streams it.

-- One interview plan per call per person on a position. The call is snapshotted as a recording's is (V151): a
-- calendar's re-read replaces its meeting rows, so the plan is matched on the call's start and title, not its row id.
CREATE TABLE app_lm_interview_plan (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id   uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id     uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    person_id      uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    candidate_id   uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    meeting_id     uuid,
    title          varchar(500),
    title_key      varchar(500) NOT NULL,
    starts_at      timestamptz  NOT NULL,
    -- [{id, section, competency, gap, text, listenFor, why, included, source}], staff-only.
    questions      jsonb        NOT NULL DEFAULT '[]'::jsonb,
    drafted_at     timestamptz,
    drafted_by     uuid         REFERENCES app_lm_user (id),
    saved_at       timestamptz,
    saved_by       uuid         REFERENCES app_lm_user (id),
    -- Claim held while one press asks the model, written by a conditional update and never by Hibernate.
    drafting_since timestamptz,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    version        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_interview_plan_call_uk UNIQUE (project_id, person_id, starts_at, title_key)
);

COMMENT ON TABLE app_lm_interview_plan IS
    'The questions a consultant prepared for one call with a person on a position. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_interview_plan_touch BEFORE UPDATE ON app_lm_interview_plan
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE INDEX app_lm_interview_plan_person_idx ON app_lm_interview_plan (workspace_id, person_id);

-- The live copilot is the bot's: chosen before it joins (Recall streams a transcript only to a bot made for it), and
-- its coaching kept beside the recording.
ALTER TABLE app_lm_meeting_recording
    ADD COLUMN live_copilot     boolean      NOT NULL DEFAULT false,
    ADD COLUMN live_enabled_by  uuid         REFERENCES app_lm_user (id),
    ADD COLUMN live_status      varchar(16)
        CONSTRAINT app_lm_meeting_recording_live_status_chk CHECK (live_status IN
            ('LISTENING', 'FEED_STOPPED', 'STOPPED')),
    ADD COLUMN live_stopped_by  uuid         REFERENCES app_lm_user (id),
    ADD COLUMN live             jsonb,
    ADD COLUMN coached_at       timestamptz,
    ADD COLUMN coached_through  bigint       NOT NULL DEFAULT 0,
    ADD COLUMN coach_runs       integer      NOT NULL DEFAULT 0,
    ADD COLUMN coach_failures   integer      NOT NULL DEFAULT 0,
    ADD COLUMN live_charged     boolean      NOT NULL DEFAULT false,
    ADD COLUMN coaching_since   timestamptz,
    ADD COLUMN live_token_hash  varchar(64),
    ADD COLUMN final_transcript_id           varchar(128),
    ADD COLUMN final_transcript_requested_at timestamptz,
    ADD CONSTRAINT app_lm_meeting_recording_live_chk CHECK (live_copilot OR live_status IS NULL);

COMMENT ON COLUMN app_lm_meeting_recording.live IS
    'The live copilot''s reading of the call so far: competency coverage, what the executive has said of the record''s '
    'gaps, the plan questions asked and up to three nudges. Staff-only; kept after the call.';
COMMENT ON COLUMN app_lm_meeting_recording.live_token_hash IS
    'SHA-256 of the token in the live transcript URL this bot was given: a delivery carrying it feeds this recording alone.';
COMMENT ON COLUMN app_lm_meeting_recording.final_transcript_id IS
    'A live copilot bot''s accuracy-mode transcript, asked of Recall after the call: the note is filed from it, never '
    'from the low-latency stream.';
COMMENT ON COLUMN app_lm_meeting_recording.coaching_since IS
    'Claim held while one coach run asks the model, so a call is coached by one run at a time; older than its deadline is stale.';

-- The transcript as Recall streams it during the call. Deleted once the final transcript is kept, or when the
-- recording ends without one, so only one copy of the call outlives it. seq is the cursor a copilot window reads after.
CREATE TABLE app_lm_meeting_live_line (
    seq             bigserial    PRIMARY KEY,
    recording_id    uuid         NOT NULL REFERENCES app_lm_meeting_recording (id) ON DELETE CASCADE,
    -- Recall's participant and the first word's offset: the same utterance delivered twice is one row.
    utterance_key   varchar(160) NOT NULL,
    participant_key varchar(80)  NOT NULL,
    speaker         varchar(200) NOT NULL,
    named           boolean      NOT NULL,
    said_at         timestamptz  NOT NULL,
    text            text         NOT NULL,
    partial         boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX app_lm_meeting_live_line_utterance_uk ON app_lm_meeting_live_line (recording_id, utterance_key)
    WHERE NOT partial;
-- One words-so-far line per speaker, replaced as Recall's partial results come in.
CREATE UNIQUE INDEX app_lm_meeting_live_line_partial_uk ON app_lm_meeting_live_line (recording_id, participant_key)
    WHERE partial;
CREATE INDEX app_lm_meeting_live_line_recording_idx ON app_lm_meeting_live_line (recording_id, seq);

COMMENT ON TABLE app_lm_meeting_live_line IS
    'A live call''s transcript, line by line as Recall streams it, for the copilot window. Staff-only; deleted when the call''s final transcript is kept.';

-- Who has a copilot window open on a live call: stamped by each window's stream as it (re)connects, at most a minute
-- apart, so every window can show who else is watching whichever instance holds their stream.
CREATE TABLE app_lm_meeting_copilot_viewer (
    recording_id uuid        NOT NULL REFERENCES app_lm_meeting_recording (id) ON DELETE CASCADE,
    user_id      uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    seen_at      timestamptz NOT NULL,
    PRIMARY KEY (recording_id, user_id)
);
