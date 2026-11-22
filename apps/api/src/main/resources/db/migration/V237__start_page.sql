-- A member's start page (docs/architecture/features.md, Start page): where Uncava opens in one workspace on a sign-in
-- and when it is opened with no link. One row per member per workspace, absent for Home. A dashboard or a position is
-- named by id and checked again on every read, so a page the member has since lost opens Home instead; deleting the
-- dashboard or the position takes the row with it. Replaces V233's landing column, whose Positions choices move here.
CREATE TABLE app_lm_start_page (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    user_id      uuid         NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    kind         varchar(16)  NOT NULL
        CONSTRAINT app_lm_start_page_kind_chk CHECK (kind IN ('POSITIONS', 'CANDIDATES', 'TASKS', 'DASHBOARD', 'POSITION')),
    dashboard_id uuid         REFERENCES app_lm_dashboard (id) ON DELETE CASCADE,
    project_id   uuid         REFERENCES app_lm_project (id) ON DELETE CASCADE,
    section      varchar(16)
        CONSTRAINT app_lm_start_page_section_chk CHECK (section IN ('BRIEF', 'STRATEGY', 'UNIVERSE', 'OUTREACH', 'DASHBOARD')),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    updated_at   timestamptz  NOT NULL DEFAULT now(),
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_start_page_member_uq UNIQUE (workspace_id, user_id),
    CONSTRAINT app_lm_start_page_target_chk CHECK (
        (kind = 'DASHBOARD' AND dashboard_id IS NOT NULL AND project_id IS NULL AND section IS NULL)
        OR (kind = 'POSITION' AND project_id IS NOT NULL AND section IS NOT NULL AND dashboard_id IS NULL)
        OR (kind IN ('POSITIONS', 'CANDIDATES', 'TASKS') AND dashboard_id IS NULL AND project_id IS NULL AND section IS NULL))
);

COMMENT ON TABLE app_lm_start_page IS
    'Where a member''s Uncava opens in one workspace when it is not Home: a list, one of their dashboards, or a position '
    'on one tab. Re-checked against the member''s access on every read. Tenant data: every read filters by workspace_id.';

CREATE TRIGGER app_lm_start_page_touch BEFORE UPDATE ON app_lm_start_page
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_start_page ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_start_page FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_start_page
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

INSERT INTO app_lm_start_page (workspace_id, user_id, kind)
SELECT workspace_id, user_id, 'POSITIONS' FROM app_lm_home_layout WHERE landing = 'POSITIONS';

ALTER TABLE app_lm_home_layout DROP COLUMN landing;
