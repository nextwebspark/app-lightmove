-- A representative can be attached to a mandate before they accept their portal invitation — the
-- mockup's promise is "invited contacts join automatically once they accept". An INVITED representative
-- has no workspace membership yet, so there is nothing to seat; the intent is parked here and turned
-- into a real CLIENT project seat by the accept listener, then the row is deleted. The pair is unique:
-- attaching twice is a no-op, not a queue.
--
-- (V16 is taken by "position brief document" on a sibling branch — this lands as V17 on purpose.)
--
-- FK choices: a deleted project makes the parked intent meaningless (CASCADE); representative rows are
-- never deleted in practice — REVOKED is a status flip and a re-invite reuses the same row id, so a
-- pending attachment deliberately survives a re-invite — but if one ever goes, its attachments go too.

CREATE TABLE app_lm_project_pending_representative (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    representative_id uuid        NOT NULL REFERENCES app_lm_client_representative (id) ON DELETE CASCADE,
    created_by        uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_project_pending_rep_uk UNIQUE (project_id, representative_id)
);

-- The accept-time lookup: every mandate waiting on this representative.
CREATE INDEX app_lm_project_pending_rep_rep_idx ON app_lm_project_pending_representative (representative_id);

CREATE TRIGGER app_lm_project_pending_representative_touch
    BEFORE UPDATE ON app_lm_project_pending_representative
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
