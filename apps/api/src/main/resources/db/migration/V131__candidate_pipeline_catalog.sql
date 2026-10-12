-- A candidate's place in a position becomes a workspace's own two-level catalog: stages (the columns of a
-- pipeline) and statuses, every status under exactly one stage. A candidate holds a status; its stage is
-- read through it, never stored beside it.
--
-- The seven statuses V36 fixed are seeded as system statuses (system_key = the old enum name) and keep
-- their wire keys, so the plugin, the public API, MCP and every old timeline line still read the same
-- tokens. They may be renamed and recoloured, never retired or moved to another stage.
--
-- Expand-only: app_lm_project_candidate.status stays, nullable and no longer written, beside the new
-- status_id, so a rollback revision still boots; it joins V91's frozen columns for #606's cleanup.

CREATE TABLE app_lm_workspace_candidate_stage (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    key           varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_candidate_stage_key_chk CHECK (key ~ '^[a-z][A-Za-z0-9]*$'),
    label         varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_candidate_stage_label_chk CHECK (btrim(label) <> ''),
    colour        varchar(16) NOT NULL DEFAULT 'NEUTRAL'
        CONSTRAINT app_lm_workspace_candidate_stage_colour_chk
            CHECK (colour IN ('GREEN', 'ACCENT', 'NEUTRAL', 'VIOLET', 'ADJACENT', 'INFERRED', 'SIGNAL', 'RED')),
    kind          varchar(8)  NOT NULL
        CONSTRAINT app_lm_workspace_candidate_stage_kind_chk CHECK (kind IN ('ACTIVE', 'CLOSED')),
    position      integer     NOT NULL,
    system_key    varchar(24)
        CONSTRAINT app_lm_workspace_candidate_stage_system_chk
            CHECK (system_key IN ('IDENTIFIED', 'CONTACTED', 'INTERESTED', 'QUALIFIED', 'SHORTLISTED', 'DECLINED')),
    retired_at    timestamptz,
    created_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0,
    -- The one closed stage can never be retired: every way out of the running lands there.
    CONSTRAINT app_lm_workspace_candidate_stage_closed_live_chk CHECK (kind = 'ACTIVE' OR retired_at IS NULL)
);

COMMENT ON TABLE app_lm_workspace_candidate_stage IS
    'A workspace''s pipeline stages. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_candidate_stage_touch BEFORE UPDATE ON app_lm_workspace_candidate_stage
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_workspace_candidate_stage_key_uk
    ON app_lm_workspace_candidate_stage (workspace_id, key);
CREATE UNIQUE INDEX app_lm_workspace_candidate_stage_label_uk
    ON app_lm_workspace_candidate_stage (workspace_id, lower(label));
CREATE UNIQUE INDEX app_lm_workspace_candidate_stage_system_uk
    ON app_lm_workspace_candidate_stage (workspace_id, system_key) WHERE system_key IS NOT NULL;
CREATE UNIQUE INDEX app_lm_workspace_candidate_stage_closed_uk
    ON app_lm_workspace_candidate_stage (workspace_id) WHERE kind = 'CLOSED';

CREATE TABLE app_lm_workspace_candidate_status (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    -- The hierarchy: a status is never free-floating.
    stage_id      uuid        NOT NULL REFERENCES app_lm_workspace_candidate_stage (id) ON DELETE RESTRICT,
    key           varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_candidate_status_key_chk CHECK (key ~ '^[a-z][A-Za-z0-9]*$'),
    label         varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_candidate_status_label_chk CHECK (btrim(label) <> ''),
    colour        varchar(16) NOT NULL DEFAULT 'NEUTRAL'
        CONSTRAINT app_lm_workspace_candidate_status_colour_chk
            CHECK (colour IN ('GREEN', 'ACCENT', 'NEUTRAL', 'VIOLET', 'ADJACENT', 'INFERRED', 'SIGNAL', 'RED')),
    position      integer     NOT NULL,
    is_default    boolean     NOT NULL DEFAULT false,
    system_key    varchar(24)
        CONSTRAINT app_lm_workspace_candidate_status_system_chk
            CHECK (system_key IN ('IDENTIFIED', 'CONTACTED', 'ENGAGED', 'INTERESTED', 'NOT_INTERESTED',
                                  'OFF_LIMITS', 'OUT_OF_SCOPE')),
    retired_at    timestamptz,
    created_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_candidate_status_system_live_chk CHECK (system_key IS NULL OR retired_at IS NULL),
    CONSTRAINT app_lm_workspace_candidate_status_default_live_chk CHECK (NOT is_default OR retired_at IS NULL)
);

COMMENT ON TABLE app_lm_workspace_candidate_status IS
    'A workspace''s candidate statuses, each under one stage. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_candidate_status_touch BEFORE UPDATE ON app_lm_workspace_candidate_status
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_workspace_candidate_status_key_uk
    ON app_lm_workspace_candidate_status (workspace_id, key);
CREATE UNIQUE INDEX app_lm_workspace_candidate_status_label_uk
    ON app_lm_workspace_candidate_status (workspace_id, lower(label));
CREATE UNIQUE INDEX app_lm_workspace_candidate_status_system_uk
    ON app_lm_workspace_candidate_status (workspace_id, system_key) WHERE system_key IS NOT NULL;
CREATE UNIQUE INDEX app_lm_workspace_candidate_status_default_uk
    ON app_lm_workspace_candidate_status (stage_id) WHERE is_default;
CREATE INDEX app_lm_workspace_candidate_status_stage_idx ON app_lm_workspace_candidate_status (stage_id);

-- The one place the starting catalog is spelled: the backfill below, CandidatePipelineCatalog for a
-- workspace founded later, and the insert trigger further down all seed through it. A workspace that
-- already has stages is left alone, and a seeding racing another lands once (ON CONFLICT).
CREATE FUNCTION app_lm_seed_candidate_pipeline(target_workspace uuid) RETURNS void
    LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_lm_workspace_candidate_stage WHERE workspace_id = target_workspace) THEN
        RETURN;
    END IF;

    INSERT INTO app_lm_workspace_candidate_stage (workspace_id, key, label, colour, kind, position, system_key)
    VALUES (target_workspace, 'identified', 'Identified', 'NEUTRAL', 'ACTIVE', 1, 'IDENTIFIED'),
           (target_workspace, 'contacted', 'Contacted', 'ACCENT', 'ACTIVE', 2, 'CONTACTED'),
           (target_workspace, 'interested', 'Interested', 'GREEN', 'ACTIVE', 3, 'INTERESTED'),
           (target_workspace, 'qualified', 'Qualified', 'ADJACENT', 'ACTIVE', 4, 'QUALIFIED'),
           (target_workspace, 'shortlisted', 'Shortlisted', 'VIOLET', 'ACTIVE', 5, 'SHORTLISTED'),
           (target_workspace, 'declined', 'Declined', 'RED', 'CLOSED', 6, 'DECLINED')
    ON CONFLICT DO NOTHING;

    INSERT INTO app_lm_workspace_candidate_status
        (workspace_id, stage_id, key, label, colour, position, is_default, system_key)
    SELECT target_workspace, stage.id, seed.key, seed.label, seed.colour, seed.position, seed.is_default,
           seed.system_key
    FROM (VALUES ('identified', 'identified', 'Identified', 'NEUTRAL', 1, true, 'IDENTIFIED'),
                 ('identified', 'researching', 'Researching', 'NEUTRAL', 2, false, NULL),
                 ('identified', 'readyToApproach', 'Ready to approach', 'NEUTRAL', 3, false, NULL),
                 ('contacted', 'contacted', 'Contacted', 'ACCENT', 1, true, 'CONTACTED'),
                 ('contacted', 'followUpSent', 'Follow-up sent', 'ACCENT', 2, false, NULL),
                 ('contacted', 'engaged', 'Engaged', 'ACCENT', 3, false, 'ENGAGED'),
                 ('contacted', 'callBooked', 'Call booked', 'ACCENT', 4, false, NULL),
                 ('interested', 'interested', 'Interested', 'GREEN', 1, true, 'INTERESTED'),
                 ('interested', 'interestedPackageGap', 'Interested – package gap', 'GREEN', 2, false, NULL),
                 ('interested', 'interestedRelocation', 'Interested – relocation concern', 'GREEN', 3, false, NULL),
                 ('interested', 'considering', 'Considering', 'GREEN', 4, false, NULL),
                 ('qualified', 'screened', 'Screened', 'ADJACENT', 1, true, NULL),
                 ('qualified', 'qualified', 'Qualified', 'ADJACENT', 2, false, NULL),
                 ('qualified', 'referencesInProgress', 'References in progress', 'ADJACENT', 3, false, NULL),
                 ('shortlisted', 'shortlisted', 'Shortlisted', 'VIOLET', 1, true, NULL),
                 ('shortlisted', 'presentedToClient', 'Presented to client', 'VIOLET', 2, false, NULL),
                 ('shortlisted', 'clientInterview', 'Client interview', 'VIOLET', 3, false, NULL),
                 ('shortlisted', 'offer', 'Offer', 'VIOLET', 4, false, NULL),
                 ('declined', 'notInterested', 'Not interested', 'NEUTRAL', 1, true, 'NOT_INTERESTED'),
                 ('declined', 'withdrew', 'Withdrew', 'NEUTRAL', 2, false, NULL),
                 ('declined', 'notAFit', 'Not a fit', 'NEUTRAL', 3, false, NULL),
                 ('declined', 'rejectedByClient', 'Rejected by client', 'NEUTRAL', 4, false, NULL),
                 ('declined', 'noResponse', 'No response', 'NEUTRAL', 5, false, NULL),
                 ('declined', 'offLimits', 'Off-limits', 'RED', 6, false, 'OFF_LIMITS'),
                 ('declined', 'outOfScope', 'Out of scope', 'SIGNAL', 7, false, 'OUT_OF_SCOPE'))
             AS seed(stage_key, key, label, colour, position, is_default, system_key)
    JOIN app_lm_workspace_candidate_stage stage
        ON stage.workspace_id = target_workspace AND stage.key = seed.stage_key
    ON CONFLICT DO NOTHING;
END;
$$;

SELECT app_lm_seed_candidate_pipeline(id) FROM app_lm_workspace;

ALTER TABLE app_lm_project_candidate
    ADD COLUMN status_id         uuid        REFERENCES app_lm_workspace_candidate_status (id) ON DELETE RESTRICT,
    ADD COLUMN status_changed_at timestamptz;

-- Every row keeps its meaning: its old enum name is the system status of the same name. Its status dates from
-- its last status line on this position, else from when it was mapped: updated_at moves with any edit. The
-- touch trigger is off for the backfill, so no executive reads as edited today.
ALTER TABLE app_lm_project_candidate DISABLE TRIGGER app_lm_project_candidate_touch;

UPDATE app_lm_project_candidate candidate
SET status_id         = status.id,
    status_changed_at = COALESCE((SELECT max(activity.occurred_at)
                                  FROM app_lm_person_activity activity
                                  WHERE activity.person_id = candidate.person_id
                                    AND activity.project_id = candidate.project_id
                                    AND activity.kind = 'STATUS_CHANGED'),
                                 candidate.created_at)
FROM app_lm_project project, app_lm_workspace_candidate_status status
WHERE project.id = candidate.project_id
  AND status.workspace_id = project.workspace_id
  AND status.system_key = candidate.status;

UPDATE app_lm_project_candidate SET status_changed_at = created_at WHERE status_changed_at IS NULL;

ALTER TABLE app_lm_project_candidate ENABLE TRIGGER app_lm_project_candidate_touch;

-- A row written by a revision that predates the catalog (a rollback) names only the old column; it lands
-- on the system status of that name, seeding the workspace first if nothing ever read its catalog.
CREATE FUNCTION app_lm_project_candidate_fill_status() RETURNS trigger
    LANGUAGE plpgsql AS $$
DECLARE
    owner_workspace uuid;
BEGIN
    IF NEW.status_id IS NULL THEN
        SELECT workspace_id INTO owner_workspace FROM app_lm_project WHERE id = NEW.project_id;
        PERFORM app_lm_seed_candidate_pipeline(owner_workspace);
        SELECT id INTO NEW.status_id FROM app_lm_workspace_candidate_status
        WHERE workspace_id = owner_workspace AND system_key = coalesce(NEW.status, 'IDENTIFIED');
    END IF;
    IF NEW.status_changed_at IS NULL THEN
        NEW.status_changed_at := COALESCE(NEW.created_at, now());
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER app_lm_project_candidate_fill_status BEFORE INSERT ON app_lm_project_candidate
    FOR EACH ROW EXECUTE FUNCTION app_lm_project_candidate_fill_status();

ALTER TABLE app_lm_project_candidate
    ALTER COLUMN status_id SET NOT NULL,
    ALTER COLUMN status_changed_at SET NOT NULL,
    ALTER COLUMN status_changed_at SET DEFAULT now(),
    ALTER COLUMN status DROP NOT NULL,
    DROP CONSTRAINT app_lm_project_candidate_status_chk;

CREATE INDEX app_lm_project_candidate_status_idx ON app_lm_project_candidate (status_id);
