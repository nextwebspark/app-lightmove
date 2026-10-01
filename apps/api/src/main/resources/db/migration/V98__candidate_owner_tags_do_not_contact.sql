-- The workspace's people as a CRM (docs/candidate-crm.md, Phase 4): who owns the relationship, who must
-- not be approached, and the team's own labels on them. All three are the person's, so every mandate
-- that maps them reads the same answer, and all three are staff working facts: none rides
-- CandidateResponse, which a client seat reads.
--
-- Only adds. The previous revision reads none of these, so the deploy window costs nothing.

ALTER TABLE app_lm_person
    -- Keeps the relationship across searches; it changes nobody's access.
    ADD COLUMN owner_user_id          uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    -- Warns on every position and turns off contact lookups; it never blocks a mapping.
    ADD COLUMN do_not_contact         boolean     NOT NULL DEFAULT false,
    ADD COLUMN do_not_contact_reason  text,
    ADD COLUMN do_not_contact_set_by  uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN do_not_contact_set_at  timestamptz,
    ADD CONSTRAINT app_lm_person_do_not_contact_chk
        CHECK (do_not_contact OR (do_not_contact_reason IS NULL AND do_not_contact_set_at IS NULL)),
    ADD CONSTRAINT app_lm_person_do_not_contact_reason_chk
        CHECK (char_length(do_not_contact_reason) <= 500);

CREATE INDEX app_lm_person_owner_idx ON app_lm_person (workspace_id, owner_user_id);

-- The workspace's tag catalog. A person holds the tag, not its spelling, so a rename reaches everyone
-- at once; a tag is retired rather than deleted, so the people who have it and their timeline keep it.
CREATE TABLE app_lm_workspace_candidate_tag (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    label         varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_candidate_tag_label_chk CHECK (btrim(label) <> ''),
    colour        varchar(16) NOT NULL DEFAULT 'NEUTRAL'
        CONSTRAINT app_lm_workspace_candidate_tag_colour_chk
            CHECK (colour IN ('GREEN', 'ACCENT', 'NEUTRAL', 'VIOLET', 'ADJACENT', 'INFERRED')),
    retired_at    timestamptz,
    created_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_workspace_candidate_tag IS
    'A workspace''s own labels on its people. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_candidate_tag_touch BEFORE UPDATE ON app_lm_workspace_candidate_tag
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- One spelling per workspace, whatever its case.
CREATE UNIQUE INDEX app_lm_workspace_candidate_tag_label_uk
    ON app_lm_workspace_candidate_tag (workspace_id, lower(label));

-- The tags a person holds (V39's owned-list idiom); who tagged them, and when, is the timeline's. The
-- service checks a tag is the person's own workspace's before it lands here.
CREATE TABLE app_lm_person_tag (
    person_id  uuid NOT NULL REFERENCES app_lm_person (id) ON DELETE CASCADE,
    tag_id     uuid NOT NULL REFERENCES app_lm_workspace_candidate_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (person_id, tag_id)
);

CREATE INDEX app_lm_person_tag_tag_idx ON app_lm_person_tag (tag_id);

-- Every workspace starts from the same handful; one created later is seeded the first time its catalog
-- is read (CandidateTagService).
INSERT INTO app_lm_workspace_candidate_tag (workspace_id, label, colour)
SELECT w.id, starter.label, starter.colour
FROM app_lm_workspace w
CROSS JOIN (VALUES ('Open to work', 'GREEN'),
                   ('Open to relocate', 'ACCENT'),
                   ('Passive', 'NEUTRAL'),
                   ('Referral', 'VIOLET'),
                   ('Prior placement', 'ADJACENT'),
                   ('Interviewed before', 'INFERRED')) AS starter(label, colour)
ON CONFLICT DO NOTHING;

ALTER TABLE app_lm_person_activity DROP CONSTRAINT app_lm_person_activity_kind_chk;
ALTER TABLE app_lm_person_activity
    ADD CONSTRAINT app_lm_person_activity_kind_chk
        CHECK (kind IN ('ADDED_TO_POOL', 'MAPPED', 'UNMAPPED', 'STATUS_CHANGED', 'PROFILE_EDITED',
                        'CONTACTS_EDITED', 'CONTACT_FOUND', 'RESEARCHED', 'AI_ASSESSED',
                        'NOTE_ADDED', 'NOTE_EDITED', 'NOTE_REMOVED',
                        'TAGGED', 'UNTAGGED', 'OWNER_CHANGED', 'DO_NOT_CONTACT_SET', 'DO_NOT_CONTACT_CLEARED'));

UPDATE app_lm_action
SET description = 'Candidates: read the workspace''s people, their notes and timeline; write notes, owner, tags and do not contact'
WHERE scope = 'WORKSPACE' AND name = 'CANDIDATE_POOL_MANAGE';
