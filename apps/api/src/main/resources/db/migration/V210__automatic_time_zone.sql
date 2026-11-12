-- A person's time zone follows their device unless they pin one (V209 made it follow the workspace). The device's
-- zone is what their browser last reported; a pinned zone stays in app_lm_user.timezone, and null there is Automatic.
ALTER TABLE app_lm_user
    ADD COLUMN device_time_zone    varchar(64),
    ADD COLUMN device_time_zone_at timestamptz;

-- The audit trail is read across every workspace below, which its row-level security allows only in maintenance.
SELECT set_config('app.rls_maintenance', 'on', true);

-- Nobody's clock moves on deploy: the zone each person now reads in becomes their device's until their browser says
-- otherwise — their own zone, or for a follower V209 cleared, the zone of the workspace they are in.
UPDATE app_lm_user u
SET device_time_zone = coalesce(u.timezone, (
    SELECT w.time_zone
    FROM app_lm_workspace_member m
    JOIN app_lm_workspace w ON w.id = m.workspace_id
    WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND w.time_zone IS NOT NULL
    ORDER BY m.joined_at
    LIMIT 1));

-- A zone is pinned only where its person chose one in Settings → Profile, which the audit trail records; their latest
-- choice stands. Everyone else is Automatic. The latest is read over every PROFILE_UPDATED line naming a zone, so a
-- choice later undone (V209's "workspace") leaves its person Automatic.
UPDATE app_lm_user u
SET timezone = chosen.zone
FROM (
    SELECT DISTINCT ON (a.actor_user_id) a.actor_user_id AS user_id, a.metadata ->> 'timezoneTo' AS zone
    FROM app_lm_audit_event a
    WHERE a.event_type = 'PROFILE_UPDATED' AND a.metadata ? 'timezoneTo'
    ORDER BY a.actor_user_id, a.occurred_at DESC, a.id DESC
) chosen
WHERE chosen.user_id = u.id;

UPDATE app_lm_user u
SET timezone = NULL
WHERE NOT EXISTS (
    SELECT 1 FROM app_lm_audit_event a
    WHERE a.actor_user_id = u.id AND a.event_type = 'PROFILE_UPDATED' AND a.metadata ? 'timezoneTo'
) OR timezone IN ('workspace', 'automatic');

SELECT set_config('app.rls_maintenance', '', true);
