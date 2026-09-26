-- A person may belong to several workspaces.
--
-- V1's "one organisation per user" followed from "an email domain identifies one organisation". That
-- premise was retired long ago (email_domain is not unique: one firm may run several workspaces), and
-- this retires its consequence: a consultant may work for two boutiques, a client representative may
-- be staff somewhere else, and a firm's admin may found a second workspace from inside the app.
--
-- A session is still in exactly ONE workspace at a time — the access token's wsId claim. Two columns
-- record which:
--   app_lm_refresh_token.workspace_id  the workspace this session (token family) is in; a refresh
--                                      re-reads the membership there, and /auth/switch-workspace is
--                                      the only thing that changes it
--   app_lm_user.last_workspace_id      the workspace the next sign-in opens in — written on every
--                                      explicit choice (sign-in, switch, create, accept), never by a
--                                      background refresh

ALTER TABLE app_lm_user
    ADD COLUMN last_workspace_id uuid REFERENCES app_lm_workspace (id) ON DELETE SET NULL;

ALTER TABLE app_lm_refresh_token
    ADD COLUMN workspace_id uuid REFERENCES app_lm_workspace (id) ON DELETE SET NULL;

-- Backfilled BEFORE the index is dropped: while it still holds, every user has at most one ACTIVE
-- row, so the UPDATE ... FROM below matches at most one membership per user.
UPDATE app_lm_user u
   SET last_workspace_id = m.workspace_id
  FROM app_lm_workspace_member m
 WHERE m.user_id = u.id AND m.status = 'ACTIVE';

UPDATE app_lm_refresh_token t
   SET workspace_id = m.workspace_id
  FROM app_lm_workspace_member m
 WHERE m.user_id = t.user_id AND m.status = 'ACTIVE' AND t.revoked_at IS NULL;

DROP INDEX app_lm_workspace_member_single_org_per_user_uk;

-- app_lm_workspace_member_uk UNIQUE (workspace_id, user_id) stays: one row per person per workspace,
-- whatever its status. A removed member who is re-invited reactivates that row rather than inserting.

COMMENT ON COLUMN app_lm_refresh_token.workspace_id IS
    'The workspace this session is in. Changed only by /auth/switch-workspace; null for a user with no workspace.';
COMMENT ON COLUMN app_lm_user.last_workspace_id IS
    'The workspace the next sign-in opens in. Written on every explicit choice, never by a background refresh.';
