-- A member's Home (docs/custom-dashboards.md, Home): the sections and library charts they keep on it, each at a place
-- on the dashboard's twelve-column grid, what Needs you shows and where they land after signing in. One row per
-- member per workspace, absent until they first save; the screen draws the default without one. Like a dashboard it
-- stores the choice and never a figure: every chart on Home is answered live. Staff-only.
--
-- tiles is a JSON array of {kind, layout: {x, y, w, h}} with kind NEEDS, TODAY, POSITIONS or CHART, a chart tile also
-- carrying {queryId, chart, options, title} as a workspace dashboard's widget does. needs maps each Needs you group
-- to whether it shows.
CREATE TABLE app_lm_home_layout (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id      uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    tiles        jsonb        NOT NULL
        CONSTRAINT app_lm_home_layout_tiles_chk CHECK (jsonb_typeof(tiles) = 'array'),
    needs        jsonb        NOT NULL DEFAULT '{}'::jsonb
        CONSTRAINT app_lm_home_layout_needs_chk CHECK (jsonb_typeof(needs) = 'object'),
    landing      varchar(16)  NOT NULL DEFAULT 'HOME'
        CONSTRAINT app_lm_home_layout_landing_chk CHECK (landing IN ('HOME', 'POSITIONS')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_home_layout_member_uq UNIQUE (workspace_id, user_id)
);

COMMENT ON TABLE app_lm_home_layout IS
    'A member''s own Home in one workspace: its tiles on the twelve-column grid, what Needs you shows and where they '
    'land; stores the choice of charts, never a figure. Tenant data, staff-only: every read filters by workspace_id.';

CREATE TRIGGER app_lm_home_layout_touch BEFORE UPDATE ON app_lm_home_layout
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_home_layout ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_home_layout FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_home_layout
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
