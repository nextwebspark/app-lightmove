-- Two-factor authentication, part 2 (epic #1080, story #1081 → #1082): a workspace may require its members to
-- hold a factor. The factor stays the person's (V162); this is only the workspace's rule about entering it, so a
-- member without one is sent to enrolment and reads nothing of this workspace until they have one.
ALTER TABLE app_lm_workspace
    ADD COLUMN require_mfa boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN app_lm_workspace.require_mfa IS
    'Every member, pure clients included, must hold an enabled two-factor factor to read this workspace.';
