-- Invite-only membership + DB-driven multi-role RBAC.
--
-- Two product decisions land together because they reshape the same tables:
--
--   1. Membership is invitation-only. The join-request path (find your firm on your email domain,
--      ask, wait for approval) is removed outright: signup always creates a workspace, and the only
--      way into an existing one is an admin naming you. PENDING_APPROVAL/REJECTED memberships and the
--      JOIN branch of the held wizard have no successor — they are deleted, not migrated.
--
--   2. Roles become data. A role grants a set of actions (app_lm_role_action), a membership holds a
--      set of roles, and permissions are the union. Adding a role or an action is an INSERT in a
--      later migration, not DDL. Code names catalog entries through enums that mirror the seeded
--      names; RbacCatalogTest fails the build if they drift.
--
-- The one-active-workspace-per-user index is deliberately untouched.

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. The join-request path dies. Pending/rejected memberships never carried access,
--    and a held JOIN wizard has nothing left to materialise into.
-- ─────────────────────────────────────────────────────────────────────────────

DELETE FROM app_lm_workspace_member WHERE status IN ('PENDING_APPROVAL', 'REJECTED');
DELETE FROM app_lm_pending_onboarding WHERE kind = 'JOIN';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. The RBAC catalog: roles, actions, and which roles grant which actions.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_role (
    id          uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    scope       varchar(16)  NOT NULL
        CONSTRAINT app_lm_role_scope_chk CHECK (scope IN ('WORKSPACE', 'PROJECT')),
    name        varchar(32)  NOT NULL,
    description text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT app_lm_role_scope_name_uk UNIQUE (scope, name),
    -- Lets assignment tables FK on (id, scope) and pin the scope — see below.
    CONSTRAINT app_lm_role_id_scope_uk   UNIQUE (id, scope)
);

COMMENT ON TABLE app_lm_role IS
    'Role catalog, seeded by migration. What a role grants lives in app_lm_role_action; who holds one lives in the assignment tables.';

CREATE TABLE app_lm_action (
    id          uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    scope       varchar(16)  NOT NULL
        CONSTRAINT app_lm_action_scope_chk CHECK (scope IN ('WORKSPACE', 'PROJECT')),
    name        varchar(64)  NOT NULL,
    description text,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    version     bigint       NOT NULL DEFAULT 0,

    CONSTRAINT app_lm_action_scope_name_uk UNIQUE (scope, name)
);

COMMENT ON TABLE app_lm_action IS
    'Action catalog — the individual permissions roles can grant. Authorisation asks for an action, never a role.';

CREATE TABLE app_lm_role_action (
    role_id   uuid NOT NULL REFERENCES app_lm_role (id) ON DELETE CASCADE,
    action_id uuid NOT NULL REFERENCES app_lm_action (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, action_id)
);

DO $$
DECLARE
    tbl text;
BEGIN
    FOREACH tbl IN ARRAY ARRAY['app_lm_role', 'app_lm_action']
    LOOP
        EXECUTE format(
                'CREATE TRIGGER %I_touch BEFORE UPDATE ON %I
                 FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at()', tbl, tbl);
    END LOOP;
END $$;

INSERT INTO app_lm_role (scope, name, description)
VALUES ('WORKSPACE', 'ADMIN',      'Governance: settings, billing, membership — and implicit project admin everywhere'),
       ('WORKSPACE', 'MEMBER',     'Staff: creates projects and works the ones they are seated on'),
       ('WORKSPACE', 'CLIENT',     'Hiring-company contact. Groundwork only — grants nothing until the client portal ships'),
       ('PROJECT',   'ADMIN',      'Owns the mandate: team, roles, client access, archival'),
       ('PROJECT',   'LEAD',       'Runs the search day-to-day. A project may have several'),
       ('PROJECT',   'RESEARCHER', 'Executes: sourcing, triage, candidates, notes'),
       ('PROJECT',   'CLIENT',     'The hiring-company seat. Groundwork only — grants nothing until the client portal ships');

INSERT INTO app_lm_action (scope, name, description)
VALUES ('WORKSPACE', 'WORKSPACE_MANAGE',     'Settings → General: rename, defaults, branding, deletion'),
       ('WORKSPACE', 'MEMBER_MANAGE',        'Change members'' roles, remove members'),
       ('WORKSPACE', 'MEMBER_INVITE',        'Send, resend, revoke and list invitations'),
       ('WORKSPACE', 'PROJECT_CREATE',       'Start a mandate, becoming its project admin'),
       ('WORKSPACE', 'PROJECT_BROWSE',       'See the workspace''s project list'),
       ('WORKSPACE', 'CLIENT_RECORD_MANAGE', 'The client registry — hiring-entity records, not client users'),
       ('PROJECT',   'PROJECT_EDIT',         'Change the mandate: target date, stage transitions'),
       ('PROJECT',   'TEAM_MANAGE',          'Seat and unseat members, change their project roles'),
       ('PROJECT',   'WORK_EXECUTE',         'Work the mandate: sourcing, triage, candidates, notes');

-- Which roles grant which actions. CLIENT roles are seeded with no actions at all — their portal
-- arrives in a later phase, and until then they can do exactly nothing.
INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('WORKSPACE', 'ADMIN',      'WORKSPACE_MANAGE'),
             ('WORKSPACE', 'ADMIN',      'MEMBER_MANAGE'),
             ('WORKSPACE', 'ADMIN',      'MEMBER_INVITE'),
             ('WORKSPACE', 'ADMIN',      'PROJECT_CREATE'),
             ('WORKSPACE', 'ADMIN',      'PROJECT_BROWSE'),
             ('WORKSPACE', 'ADMIN',      'CLIENT_RECORD_MANAGE'),
             ('WORKSPACE', 'MEMBER',     'PROJECT_CREATE'),
             ('WORKSPACE', 'MEMBER',     'PROJECT_BROWSE'),
             ('WORKSPACE', 'MEMBER',     'CLIENT_RECORD_MANAGE'),
             ('PROJECT',   'ADMIN',      'PROJECT_EDIT'),
             ('PROJECT',   'ADMIN',      'TEAM_MANAGE'),
             ('PROJECT',   'ADMIN',      'WORK_EXECUTE'),
             ('PROJECT',   'LEAD',       'PROJECT_EDIT'),
             ('PROJECT',   'LEAD',       'WORK_EXECUTE'),
             ('PROJECT',   'RESEARCHER', 'WORK_EXECUTE')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Assignment tables. The composite FK on (role_id, role_scope) with a CHECKed
--    scope column is what makes cross-scope assignment unrepresentable: a PROJECT
--    role physically cannot be attached to a workspace membership.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE app_lm_workspace_member_role (
    member_id  uuid        NOT NULL REFERENCES app_lm_workspace_member (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL,
    role_scope varchar(16) NOT NULL DEFAULT 'WORKSPACE'
        CONSTRAINT app_lm_workspace_member_role_scope_chk CHECK (role_scope = 'WORKSPACE'),

    FOREIGN KEY (role_id, role_scope) REFERENCES app_lm_role (id, scope),
    PRIMARY KEY (member_id, role_id)
);

CREATE INDEX app_lm_workspace_member_role_role_idx ON app_lm_workspace_member_role (role_id);

CREATE TABLE app_lm_project_member_role (
    project_member_id uuid        NOT NULL REFERENCES app_lm_project_member (id) ON DELETE CASCADE,
    role_id           uuid        NOT NULL,
    role_scope        varchar(16) NOT NULL DEFAULT 'PROJECT'
        CONSTRAINT app_lm_project_member_role_scope_chk CHECK (role_scope = 'PROJECT'),

    FOREIGN KEY (role_id, role_scope) REFERENCES app_lm_role (id, scope),
    PRIMARY KEY (project_member_id, role_id)
);

CREATE INDEX app_lm_project_member_role_role_idx ON app_lm_project_member_role (role_id);

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Migrate the single-role columns into assignments.
--    CONSULTANT and RESEARCHER were never distinguished by any check in the code;
--    both collapse to MEMBER. Project MEMBER seats become RESEARCHER.
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO app_lm_workspace_member_role (member_id, role_id)
SELECT wm.id, r.id
FROM app_lm_workspace_member wm
JOIN app_lm_role r ON r.scope = 'WORKSPACE'
                  AND r.name = CASE wm.role WHEN 'ADMIN' THEN 'ADMIN' ELSE 'MEMBER' END;

INSERT INTO app_lm_project_member_role (project_member_id, role_id)
SELECT pm.id, r.id
FROM app_lm_project_member pm
JOIN app_lm_role r ON r.scope = 'PROJECT'
                  AND r.name = CASE pm.role WHEN 'LEAD' THEN 'LEAD' ELSE 'RESEARCHER' END;

-- Creators become project admins. Every project's creator gets a seat (they never had one unless they
-- were the lead) holding ADMIN and LEAD — the new create() seeds exactly that, and the backfill makes
-- old projects obey the same invariant the last-admin guard will assume.
INSERT INTO app_lm_project_member (project_id, member_id, role, added_by)
SELECT p.id, wm.id, 'MEMBER', p.created_by
FROM app_lm_project p
JOIN app_lm_workspace_member wm ON wm.workspace_id = p.workspace_id
                               AND wm.user_id = p.created_by
                               AND wm.status = 'ACTIVE'
ON CONFLICT (project_id, member_id) DO NOTHING;

INSERT INTO app_lm_project_member_role (project_member_id, role_id)
SELECT pm.id, r.id
FROM app_lm_project p
JOIN app_lm_workspace_member wm ON wm.workspace_id = p.workspace_id AND wm.user_id = p.created_by
JOIN app_lm_project_member pm ON pm.project_id = p.id AND pm.member_id = wm.id
JOIN app_lm_role r ON r.scope = 'PROJECT' AND r.name IN ('ADMIN', 'LEAD')
ON CONFLICT DO NOTHING;

-- Repair: a project whose creator's membership is gone would still have no admin. Promote its leads.
-- A project with neither creator nor lead stays admin-less and remains manageable through the
-- workspace-admin bypass — acceptable, and impossible for anything created from now on.
INSERT INTO app_lm_project_member_role (project_member_id, role_id)
SELECT pm.id, (SELECT id FROM app_lm_role WHERE scope = 'PROJECT' AND name = 'ADMIN')
FROM app_lm_project_member pm
JOIN app_lm_project_member_role pmr ON pmr.project_member_id = pm.id
JOIN app_lm_role lead ON lead.id = pmr.role_id AND lead.scope = 'PROJECT' AND lead.name = 'LEAD'
WHERE NOT EXISTS (SELECT 1
                  FROM app_lm_project_member pm2
                  JOIN app_lm_project_member_role pmr2 ON pmr2.project_member_id = pm2.id
                  JOIN app_lm_role admin ON admin.id = pmr2.role_id
                                        AND admin.scope = 'PROJECT' AND admin.name = 'ADMIN'
                  WHERE pm2.project_id = pm.project_id)
ON CONFLICT DO NOTHING;

-- ─────────────────────────────────────────────────────────────────────────────
-- 5. Invitations now reference the catalog, and gain the client-portal groundwork.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE app_lm_invitation ADD COLUMN role_id uuid REFERENCES app_lm_role (id);

UPDATE app_lm_invitation i
SET role_id = r.id
FROM app_lm_role r
WHERE r.scope = 'WORKSPACE'
  AND r.name = CASE i.role WHEN 'ADMIN' THEN 'ADMIN' ELSE 'MEMBER' END;

ALTER TABLE app_lm_invitation ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE app_lm_invitation DROP COLUMN role;

-- A CLIENT invitation is scoped to one project; a staff invitation is not scoped at all. The service
-- layer refuses to mint CLIENT invites until the portal ships, and this CHECK holds the shape honest
-- either way. (It compares against the seeded CLIENT role id, computed at migration time — role ids
-- are stable from here on.)
ALTER TABLE app_lm_invitation
    ADD COLUMN project_id uuid REFERENCES app_lm_project (id) ON DELETE CASCADE;

DO $$
DECLARE
    client_role uuid;
BEGIN
    SELECT id INTO client_role FROM app_lm_role WHERE scope = 'WORKSPACE' AND name = 'CLIENT';
    EXECUTE format(
            'ALTER TABLE app_lm_invitation ADD CONSTRAINT app_lm_invitation_client_project_chk
             CHECK ((role_id = %L) = (project_id IS NOT NULL))', client_role);
END $$;

-- ─────────────────────────────────────────────────────────────────────────────
-- 6. Drop the single-role machinery and the join-request states.
-- ─────────────────────────────────────────────────────────────────────────────

-- Multiple leads are now legal; the one-lead index has no successor.
DROP INDEX app_lm_project_member_lead_uk;

ALTER TABLE app_lm_project_member DROP COLUMN role;
ALTER TABLE app_lm_workspace_member DROP COLUMN role;

ALTER TABLE app_lm_workspace_member DROP CONSTRAINT app_lm_workspace_member_status_chk;
ALTER TABLE app_lm_workspace_member
    ADD CONSTRAINT app_lm_workspace_member_status_chk
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REMOVED'));

-- ─────────────────────────────────────────────────────────────────────────────
-- 7. The held wizard only ever creates now. JOIN rows were deleted in step 1;
--    the columns and constraints that carried them go too.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE app_lm_pending_onboarding DROP CONSTRAINT app_lm_pending_onboarding_create_chk;
ALTER TABLE app_lm_pending_onboarding DROP CONSTRAINT app_lm_pending_onboarding_join_chk;
ALTER TABLE app_lm_pending_onboarding DROP COLUMN kind;
ALTER TABLE app_lm_pending_onboarding DROP COLUMN workspace_id;
ALTER TABLE app_lm_pending_onboarding DROP COLUMN requested_role;
ALTER TABLE app_lm_pending_onboarding ALTER COLUMN name SET NOT NULL;

-- Held step-3 invites carry role names inside jsonb; collapse the old ones there too.
UPDATE app_lm_pending_onboarding
SET invitations = (SELECT coalesce(jsonb_agg(
                                  CASE
                                      WHEN elem ->> 'role' IN ('CONSULTANT', 'RESEARCHER')
                                          THEN jsonb_set(elem, '{role}', '"MEMBER"')
                                      ELSE elem END), '[]'::jsonb)
                   FROM jsonb_array_elements(invitations) elem)
WHERE invitations <> '[]'::jsonb;

COMMENT ON TABLE app_lm_pending_onboarding IS
    'A completed signup wizard awaiting email verification. Materialised into a workspace by VerificationService; never visible on the domain until then.';

-- ─────────────────────────────────────────────────────────────────────────────
-- 8. Signup no longer looks up workspaces by domain — the listing fed the join
--    fork, and the join fork is gone. The email_domain column itself stays: it
--    still records which firm a workspace belongs to.
-- ─────────────────────────────────────────────────────────────────────────────

DROP INDEX app_lm_workspace_domain_idx;
