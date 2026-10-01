-- Notes are the person's, shared by every mandate that maps them (docs/candidate-crm.md, Phase 3).
--
-- Until now each mandate kept one free-text note per executive, on the mapping row, and a client seat
-- read it. A note is now a typed, authored, timed row on the person, with the mandate it was written
-- about as optional context, and staff-only: no client seat reads one (decision D1).
--
-- app_lm_project_candidate.note is copied here and left where it is, part of V91's frozen copy that the
-- final cleanup migration drops (issue #606). Nothing reads or writes it from here.

CREATE TABLE app_lm_person_note (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    person_id       uuid        NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    -- The mandate the note was written about; its title is kept so the note outlives it.
    project_id      uuid        REFERENCES app_lm_project (id) ON DELETE SET NULL,
    project_title   text,
    kind            varchar(16) NOT NULL
        CONSTRAINT app_lm_person_note_kind_chk CHECK (kind IN ('GENERAL', 'CALL', 'MEETING', 'EMAIL')),
    body            text        NOT NULL
        CONSTRAINT app_lm_person_note_body_chk CHECK (btrim(body) <> '' AND char_length(body) <= 4000),
    pinned          boolean     NOT NULL DEFAULT false,
    author_user_id  uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    -- A change to the text or kind by a person; pinning is not an edit.
    edited_at       timestamptz,
    edited_by       uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    version         bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_person_note IS
    'Notes on a workspace person, shared by every mandate that maps them. Staff-only tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_person_note_touch BEFORE UPDATE ON app_lm_person_note
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE INDEX app_lm_person_note_person_idx ON app_lm_person_note (person_id, pinned DESC, created_at DESC);
CREATE INDEX app_lm_person_note_workspace_idx ON app_lm_person_note (workspace_id, created_at DESC);

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED'));

-- Each mandate's note becomes a general note about that mandate, written by whoever filed the row, as of
-- its last edit; and a timeline line saying so, so old notes read on the timeline like new ones.
INSERT INTO app_lm_person_note (workspace_id, person_id, project_id, project_title, kind, body,
                                author_user_id, created_at, updated_at)
SELECT p.workspace_id, c.person_id, c.project_id, pr.position_title, 'GENERAL', btrim(c.note),
       c.added_by, c.updated_at, c.updated_at
FROM app_lm_project_candidate c
JOIN app_lm_person p ON p.id = c.person_id
JOIN app_lm_project pr ON pr.id = c.project_id
WHERE btrim(coalesce(c.note, '')) <> '';

INSERT INTO app_lm_person_activity (workspace_id, person_id, project_id, project_title, actor_user_id,
                                    kind, occurred_at, details)
SELECT n.workspace_id, n.person_id, n.project_id, n.project_title, n.author_user_id, 'NOTE_ADDED',
       n.created_at, jsonb_build_object('noteId', n.id, 'kind', n.kind)
FROM app_lm_person_note n
ORDER BY n.created_at, n.id;
