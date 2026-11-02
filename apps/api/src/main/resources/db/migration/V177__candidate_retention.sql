-- Candidate retention (#921, part 2): how long a workspace keeps a candidate nobody is working with, and the people
-- past it waiting for an admin. Nothing deletes on its own: whoever is due waits under Settings → Data & retention
-- until an admin deletes them (through PersonErasureService, as any erasure) or keeps them for another period.
--
-- A person is due once their last touch — their latest timeline line, an admin's keep, or their founding — is older
-- than the period and no live position maps them. A restricted person (V175) is never due: a request is pending.
--
-- No row is the deployment's default (lightmove.retention.candidate-months); a row is an admin's choice, and a null
-- period on it is "never delete". The floor is a year: a shorter period would offer people still being worked.

CREATE TABLE app_lm_workspace_retention (
    id                         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id               uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    candidate_retention_months integer
        CONSTRAINT app_lm_workspace_retention_months_chk CHECK (candidate_retention_months BETWEEN 12 AND 120),
    created_at                 timestamptz NOT NULL DEFAULT now(),
    updated_at                 timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_workspace_retention_workspace_uk UNIQUE (workspace_id)
);

COMMENT ON TABLE app_lm_workspace_retention IS
    'How long a workspace keeps a candidate nobody is working with; no row is the deployment''s default, a null period never. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_workspace_retention_touch BEFORE UPDATE ON app_lm_workspace_retention
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_workspace_retention ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_retention FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_retention
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- An admin's Keep: the person counts as touched then, so they are due again one period later.
ALTER TABLE app_lm_person
    ADD COLUMN retention_kept_at timestamptz,
    ADD COLUMN retention_kept_by uuid REFERENCES app_lm_user (id) ON DELETE SET NULL;

-- The due list reads each person's latest line.
CREATE INDEX app_lm_person_activity_person_occurred_idx ON app_lm_person_activity (person_id, occurred_at DESC);
