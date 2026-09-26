-- V81's two workspace pointers are ON DELETE SET NULL foreign keys, so deleting a workspace looks
-- both of them up. app_lm_refresh_token gains a row per rotation and is the fastest-growing auth
-- table; without these a workspace delete scans it whole.
CREATE INDEX app_lm_refresh_token_workspace_idx
    ON app_lm_refresh_token (workspace_id) WHERE workspace_id IS NOT NULL;

CREATE INDEX app_lm_user_last_workspace_idx
    ON app_lm_user (last_workspace_id) WHERE last_workspace_id IS NOT NULL;

-- V81's comment said a switch was the only thing that moves a session. A web refresh also moves it
-- when the member was removed from the workspace it was in (audited as WORKSPACE_SWITCHED,
-- reason MEMBERSHIP_ENDED); an extension session never moves.
COMMENT ON COLUMN app_lm_refresh_token.workspace_id IS
    'The workspace this session is in. Moved by /auth/switch-workspace, or by a web refresh once the membership has ended; null for a session in no workspace.';
