-- Why someone left the process: a workspace's own list of reasons, each offered at the out-of-process statuses it is
-- linked to. A link only decides what a status offers; a reason is retired, never deleted, so whoever was given one
-- keeps it. Staff-only: a client seat never reads why a candidate left.
CREATE TABLE app_lm_workspace_exit_reason (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    key           varchar(40) NOT NULL
        CONSTRAINT app_lm_workspace_exit_reason_key_chk CHECK (key ~ '^[a-z][A-Za-z0-9]*$'),
    label         varchar(60) NOT NULL
        CONSTRAINT app_lm_workspace_exit_reason_label_chk CHECK (btrim(label) <> ''),
    requires_note boolean     NOT NULL DEFAULT false,
    position      integer     NOT NULL,
    retired_at    timestamptz,
    created_by    uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_workspace_exit_reason IS
    'Why someone left a position''s process, as a workspace words it. Tenant data, staff-only.';

CREATE TRIGGER app_lm_workspace_exit_reason_touch BEFORE UPDATE ON app_lm_workspace_exit_reason
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE UNIQUE INDEX app_lm_workspace_exit_reason_key_uk ON app_lm_workspace_exit_reason (workspace_id, key);
CREATE UNIQUE INDEX app_lm_workspace_exit_reason_label_uk
    ON app_lm_workspace_exit_reason (workspace_id, lower(label));

-- Which out-of-process statuses offer a reason. A status that offers none asks for none.
CREATE TABLE app_lm_workspace_status_exit_reason (
    reason_id uuid NOT NULL REFERENCES app_lm_workspace_exit_reason (id) ON DELETE CASCADE,
    status_id uuid NOT NULL REFERENCES app_lm_workspace_candidate_status (id) ON DELETE CASCADE,
    PRIMARY KEY (reason_id, status_id)
);

CREATE INDEX app_lm_workspace_status_exit_reason_status_idx ON app_lm_workspace_status_exit_reason (status_id);

ALTER TABLE app_lm_workspace_exit_reason ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_exit_reason FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_exit_reason
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

ALTER TABLE app_lm_workspace_status_exit_reason ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_status_exit_reason FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_status_exit_reason
    USING (EXISTS (SELECT 1 FROM app_lm_workspace_exit_reason parent
                   WHERE parent.id = app_lm_workspace_status_exit_reason.reason_id))
    WITH CHECK (EXISTS (SELECT 1 FROM app_lm_workspace_exit_reason parent
                        WHERE parent.id = app_lm_workspace_status_exit_reason.reason_id));

-- The starting list ALAC Partners worked out, linked by status key to the ways out it belongs to, and only where that
-- status is still a live way out. A workspace already holding any reason is left alone, so this runs once per
-- workspace: here for every workspace whose pipeline exists, and on a first read of the reasons for the rest.
CREATE FUNCTION app_lm_seed_exit_reasons(target_workspace uuid) RETURNS void
    LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM app_lm_workspace_exit_reason WHERE workspace_id = target_workspace) THEN
        RETURN;
    END IF;

    INSERT INTO app_lm_workspace_exit_reason (workspace_id, key, label, requires_note, position)
    VALUES (target_workspace, 'location', 'Location', false, 1),
           (target_workspace, 'senior', 'Senior', false, 2),
           (target_workspace, 'junior', 'Junior', false, 3),
           (target_workspace, 'technicalFit', 'Technical fit', false, 4),
           (target_workspace, 'culturalFit', 'Cultural fit', false, 5),
           (target_workspace, 'language', 'Language', false, 6),
           (target_workspace, 'timing', 'Timing', false, 7),
           (target_workspace, 'company', 'Company', false, 8),
           (target_workspace, 'scope', 'Scope', false, 9),
           (target_workspace, 'compensation', 'Compensation', false, 10),
           (target_workspace, 'negativeFeedback', 'Negative feedback', false, 11),
           (target_workspace, 'anotherOffer', 'Another offer', false, 12),
           (target_workspace, 'other', 'Other', true, 13)
    ON CONFLICT DO NOTHING;

    INSERT INTO app_lm_workspace_status_exit_reason (reason_id, status_id)
    SELECT reason.id, status.id
    FROM (VALUES ('outOfScope', 'location'), ('outOfScope', 'senior'), ('outOfScope', 'junior'),
                 ('outOfScope', 'technicalFit'), ('outOfScope', 'culturalFit'), ('outOfScope', 'language'),
                 ('outOfScope', 'other'),
                 ('notInterested', 'timing'), ('notInterested', 'company'), ('notInterested', 'scope'),
                 ('notInterested', 'compensation'), ('notInterested', 'location'), ('notInterested', 'other'),
                 ('notProgressed', 'technicalFit'), ('notProgressed', 'culturalFit'),
                 ('notProgressed', 'compensation'), ('notProgressed', 'senior'), ('notProgressed', 'junior'),
                 ('notProgressed', 'negativeFeedback'), ('notProgressed', 'other'),
                 ('clientRejected', 'technicalFit'), ('clientRejected', 'culturalFit'), ('clientRejected', 'senior'),
                 ('clientRejected', 'junior'), ('clientRejected', 'negativeFeedback'), ('clientRejected', 'other'),
                 ('withdrew', 'anotherOffer'), ('withdrew', 'compensation'), ('withdrew', 'other'),
                 ('withdrewShortlist', 'anotherOffer'), ('withdrewShortlist', 'compensation'),
                 ('withdrewShortlist', 'other'),
                 ('withdrewOffer', 'anotherOffer'), ('withdrewOffer', 'compensation'), ('withdrewOffer', 'other'))
             AS seed(status_key, reason_key)
    JOIN app_lm_workspace_candidate_status status
        ON status.workspace_id = target_workspace AND status.key = seed.status_key
       AND status.out_of_process AND status.retired_at IS NULL
    JOIN app_lm_workspace_exit_reason reason
        ON reason.workspace_id = target_workspace AND reason.key = seed.reason_key
    ON CONFLICT DO NOTHING;
END;
$$;

SELECT app_lm_seed_exit_reasons(workspace.id)
FROM app_lm_workspace workspace
WHERE EXISTS (SELECT 1 FROM app_lm_workspace_candidate_stage stage WHERE stage.workspace_id = workspace.id);
