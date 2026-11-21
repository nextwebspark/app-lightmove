-- A member's custom dashboard (docs/custom-dashboards.md): a named, ordered list of charts picked from the
-- server's query library, on one position (project_id) or across the workspace (project_id null). It stores the
-- choice — which query, how it is drawn, its options, title and size — never a figure: every chart is answered
-- live, so a dashboard can never show a number its reader could not read today.
--
-- widgets is a JSON array of {queryId, chart, options, title, size}; a query retired from the library stays in
-- the array and is drawn as no longer available rather than rewritten. Private to owner_user_id unless shared,
-- and then read by the position's staff or the workspace's. Staff-only: a client seat reaches no dashboard.
CREATE TABLE app_lm_dashboard (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id    uuid,
    owner_user_id uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    name          varchar(80)  NOT NULL
        CONSTRAINT app_lm_dashboard_name_chk CHECK (btrim(name) <> ''),
    shared        boolean      NOT NULL DEFAULT false,
    widgets       jsonb        NOT NULL DEFAULT '[]'::jsonb
        CONSTRAINT app_lm_dashboard_widgets_chk CHECK (jsonb_typeof(widgets) = 'array'),
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_dashboard_project_fk
        FOREIGN KEY (project_id, workspace_id) REFERENCES app_lm_project (id, workspace_id) ON DELETE CASCADE
);

CREATE INDEX app_lm_dashboard_scope_idx ON app_lm_dashboard (workspace_id, project_id);

COMMENT ON TABLE app_lm_dashboard IS
    'A member''s custom dashboard over the query library, on a position or the workspace; stores the choice of '
    'charts, never a figure. Tenant data, staff-only: every read filters by workspace_id.';

CREATE TRIGGER app_lm_dashboard_touch BEFORE UPDATE ON app_lm_dashboard
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_dashboard ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_dashboard FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_dashboard
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
