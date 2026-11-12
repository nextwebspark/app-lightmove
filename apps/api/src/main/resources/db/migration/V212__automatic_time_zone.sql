-- A person's time zone follows their device unless they pin one. A pinned zone stays in app_lm_user.timezone, where
-- null is Automatic; the device's is what their browser last reported.
ALTER TABLE app_lm_user
    ADD COLUMN device_time_zone    varchar(64),
    ADD COLUMN device_time_zone_at timestamptz;

-- The audit trail is read across every workspace, which its row-level security allows only in maintenance.
DO $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);

    -- Nobody's clock moves on deploy: the zone each person reads in now is their device's until their browser says
    -- otherwise — their own, or for a follower V211 cleared, their first workspace's.
    UPDATE app_lm_user u
    SET device_time_zone = coalesce(u.timezone, (
        SELECT w.time_zone
        FROM app_lm_workspace_member m
        JOIN app_lm_workspace w ON w.id = m.workspace_id
        WHERE m.user_id = u.id AND m.status = 'ACTIVE' AND w.time_zone IS NOT NULL
        ORDER BY m.joined_at
        LIMIT 1));

    -- Pinned only where the person chose a zone in Settings → Profile, as the audit trail records, and their latest
    -- choice names a zone; everyone else is Automatic.
    UPDATE app_lm_user u
    SET timezone = (
        SELECT CASE WHEN latest.zone IN (SELECT name FROM pg_timezone_names) THEN latest.zone END
        FROM (
            SELECT a.metadata ->> 'timezoneTo' AS zone
            FROM app_lm_audit_event a
            WHERE a.actor_user_id = u.id AND a.event_type = 'PROFILE_UPDATED' AND a.metadata ? 'timezoneTo'
            ORDER BY a.occurred_at DESC, a.id DESC
            LIMIT 1
        ) latest);

    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END $$;
