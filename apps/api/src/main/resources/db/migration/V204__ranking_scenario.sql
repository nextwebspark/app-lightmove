-- A position's ranking scenario (the Dashboard's Ranking chapter): one shared what-if over the assessed
-- candidates — a technical share and per-competency weights that differ from the brief's — with the team's
-- rationale. It never writes the brief; the brief's own weights are the agreed baseline, and no row is that
-- baseline, so a later brief edit leaves no stale copy of it behind.
--
-- weights is keyed on a competency's match key (panel and lower-cased name, as a scorecard's score is), since a
-- brief's competencies carry no stable id. saved_against is the brief's share and weights as they stood at the
-- save, so the screen can say the brief changed since. Staff-only, like the scorecards it reads.
CREATE TABLE app_lm_ranking_scenario (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id      uuid        NOT NULL,
    technical_share integer     NOT NULL
        CONSTRAINT app_lm_ranking_scenario_share_chk CHECK (technical_share BETWEEN 0 AND 100),
    weights         jsonb       NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT app_lm_ranking_scenario_weights_chk CHECK (jsonb_typeof(weights) = 'object'),
    saved_against   jsonb       NOT NULL
        CONSTRAINT app_lm_ranking_scenario_against_chk CHECK (jsonb_typeof(saved_against) = 'object'),
    rationale       varchar(1000)
        CONSTRAINT app_lm_ranking_scenario_rationale_chk CHECK (rationale IS NULL OR btrim(rationale) <> ''),
    updated_by      uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    version         bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_ranking_scenario_project_uk UNIQUE (project_id),
    CONSTRAINT app_lm_ranking_scenario_project_fk
        FOREIGN KEY (project_id, workspace_id) REFERENCES app_lm_project (id, workspace_id) ON DELETE CASCADE
);

COMMENT ON TABLE app_lm_ranking_scenario IS
    'A position''s one shared ranking scenario over its assessed candidates; never the brief. Tenant data, '
    'staff-only: every read filters by workspace_id.';

CREATE TRIGGER app_lm_ranking_scenario_touch BEFORE UPDATE ON app_lm_ranking_scenario
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_ranking_scenario ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_ranking_scenario FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_ranking_scenario
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
