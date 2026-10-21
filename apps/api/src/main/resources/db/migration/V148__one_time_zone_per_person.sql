-- One clock per person: Settings → Profile's time zone is the one their mailboxes send by, and the digest goes in.
-- A mailbox's zone is the one that actually sent, so where someone has one it becomes theirs (the latest connected
-- where they hold several) and nothing a live sequence does moves on deploy.
UPDATE app_lm_user u
SET timezone = latest.time_zone
FROM (
    SELECT DISTINCT ON (user_id) user_id, time_zone
    FROM app_lm_mailbox_connection
    ORDER BY user_id, connected_at DESC
) latest
WHERE latest.user_id = u.id
  AND u.timezone <> latest.time_zone;

-- The profile picker offered Etc/GMT; a person's zone is now a region's own or UTC.
UPDATE app_lm_user
SET timezone = 'UTC'
WHERE timezone IN ('Etc/GMT', 'Etc/UTC', 'GMT');

UPDATE app_lm_mailbox_connection m
SET time_zone = u.timezone
FROM app_lm_user u
WHERE u.id = m.user_id
  AND m.time_zone <> u.timezone;
