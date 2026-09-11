-- A third tier of role above the workspace: LightMove's own staff, who curate what every tenant shares.
--
-- Until now every privilege was a workspace's to grant. The role-template library (V42) is the first
-- thing no workspace owns and someone still has to edit, so it needs a holder outside every tenant.
-- PLATFORM joins the two scope CHECKs and follows V6's shape exactly: a role, the actions it grants,
-- and an assignment table whose composite foreign key pins the scope.
--
-- A platform role reads no tenant data. It gates platform endpoints only; every workspace-scoped read
-- still filters on the caller's own membership, and a super admin is an ordinary member of their own
-- workspace besides.
--
-- Assignments are written by ops/cloudsql/grant-platform-role.sh, never by the application, and
-- harden.sql leaves the runtime role SELECT on app_lm_user_platform_role — a foothold in the app
-- cannot mint a super admin.

ALTER TABLE app_lm_role DROP CONSTRAINT app_lm_role_scope_chk;
ALTER TABLE app_lm_role
    ADD CONSTRAINT app_lm_role_scope_chk CHECK (scope IN ('WORKSPACE', 'PROJECT', 'PLATFORM'));

ALTER TABLE app_lm_action DROP CONSTRAINT app_lm_action_scope_chk;
ALTER TABLE app_lm_action
    ADD CONSTRAINT app_lm_action_scope_chk CHECK (scope IN ('WORKSPACE', 'PROJECT', 'PLATFORM'));

INSERT INTO app_lm_role (scope, name, description)
VALUES ('PLATFORM', 'SUPER_ADMIN', 'LightMove staff: curates the libraries every workspace shares. Reads no tenant data');

INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM',  'TEMPLATE_LIBRARY_MANAGE',  'Edit, add, archive and import the shared role-template library'),
       ('WORKSPACE', 'POSITION_TEMPLATE_MANAGE', 'Settings → Templates: customise, hide, add and import the firm''s role templates');

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM (VALUES ('PLATFORM',  'SUPER_ADMIN', 'TEMPLATE_LIBRARY_MANAGE'),
             ('WORKSPACE', 'ADMIN',       'POSITION_TEMPLATE_MANAGE')
     ) AS grant_map(scope, role_name, action_name)
JOIN app_lm_role   r ON r.scope = grant_map.scope AND r.name = grant_map.role_name
JOIN app_lm_action a ON a.scope = grant_map.scope AND a.name = grant_map.action_name;

CREATE TABLE app_lm_user_platform_role (
    user_id    uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL,
    role_scope varchar(16) NOT NULL DEFAULT 'PLATFORM'
        CONSTRAINT app_lm_user_platform_role_scope_chk CHECK (role_scope = 'PLATFORM'),
    granted_at timestamptz NOT NULL DEFAULT now(),

    FOREIGN KEY (role_id, role_scope) REFERENCES app_lm_role (id, scope),
    PRIMARY KEY (user_id, role_id)
);

COMMENT ON TABLE app_lm_user_platform_role IS
    'Platform roles a user holds outside any workspace. Written by ops/cloudsql/grant-platform-role.sh only; the runtime role reads it.';
