-- Outreach sequences and who is in them (epic #620, story #623). Nothing here sends: an enrollment is
-- created SCHEDULED, with its first email already written and reviewed, and story #624's dispatcher
-- takes it from there.

CREATE TABLE app_lm_outreach_sequence (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id      uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    name            varchar(120) NOT NULL,
    created_by      uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

CREATE INDEX app_lm_outreach_sequence_project_idx ON app_lm_outreach_sequence (project_id);

COMMENT ON TABLE app_lm_outreach_sequence IS
    'A position''s outreach sequence. Staff-only tenant data: every read filters by workspace_id and project_id.';

CREATE TRIGGER app_lm_outreach_sequence_touch BEFORE UPDATE ON app_lm_outreach_sequence
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- V39's owned list: the editor saves every step at once and the list is replaced wholesale. Step 1 is
-- sort_order 0, carries the subject and goes when the consultant starts; the others are replies in its
-- thread, each a number of working days after the one before.
CREATE TABLE app_lm_outreach_sequence_step (
    sequence_id         uuid         NOT NULL REFERENCES app_lm_outreach_sequence (id) ON DELETE CASCADE,
    sort_order          integer      NOT NULL
        CONSTRAINT app_lm_outreach_sequence_step_order_chk CHECK (sort_order BETWEEN 0 AND 2),
    delay_working_days  integer      NOT NULL
        CONSTRAINT app_lm_outreach_sequence_step_delay_chk CHECK (delay_working_days BETWEEN 0 AND 30),
    subject             varchar(200),
    body                text         NOT NULL,
    PRIMARY KEY (sequence_id, sort_order)
);

-- One person on one sequence. The first email is frozen here as the consultant reviewed it, opener
-- and all; the follow-ups are rendered from the sequence when they go, so an edit to the sequence
-- reaches people already in it from their next step. candidate_id is SET NULL rather than CASCADE:
-- removing someone from a position stops their outreach (the dispatcher re-checks) without erasing
-- that it happened.
CREATE TABLE app_lm_outreach_enrollment (
    id                uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id        uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    sequence_id       uuid         NOT NULL REFERENCES app_lm_outreach_sequence (id) ON DELETE RESTRICT,
    candidate_id      uuid         REFERENCES app_lm_project_candidate (id) ON DELETE SET NULL,
    person_id         uuid         NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    sender_user_id    uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    to_address        varchar(320) NOT NULL,
    status            varchar(16)  NOT NULL
        CONSTRAINT app_lm_outreach_enrollment_status_chk
            CHECK (status IN ('SCHEDULED', 'ACTIVE', 'REPLIED', 'BOUNCED', 'STOPPED', 'COMPLETED')),
    stop_reason       varchar(32),
    next_step         integer      NOT NULL DEFAULT 0,
    next_send_at      timestamptz,
    thread_id         varchar(128),
    opener            text,
    opener_edited     boolean      NOT NULL DEFAULT false,
    first_subject     varchar(300) NOT NULL,
    first_body        text         NOT NULL,
    enrolled_by       uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    enrolled_at       timestamptz  NOT NULL,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    version           bigint       NOT NULL DEFAULT 0
);

-- One live enrollment per person per position: two sequences must never approach one executive at once.
CREATE UNIQUE INDEX app_lm_outreach_enrollment_live_uk ON app_lm_outreach_enrollment (project_id, person_id)
    WHERE status IN ('SCHEDULED', 'ACTIVE');

CREATE INDEX app_lm_outreach_enrollment_sequence_idx ON app_lm_outreach_enrollment (sequence_id);
CREATE INDEX app_lm_outreach_enrollment_candidate_idx ON app_lm_outreach_enrollment (candidate_id);

COMMENT ON TABLE app_lm_outreach_enrollment IS
    'A person on an outreach sequence, with their reviewed first email. Staff-only tenant data.';

CREATE TRIGGER app_lm_outreach_enrollment_touch BEFORE UPDATE ON app_lm_outreach_enrollment
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED',
                        'OUTREACH_ENROLLED'));
