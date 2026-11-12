-- How dates and times read: the workspace's time zone, date format and time format, chosen by an admin, and each
-- person's own choice of any of the three above it. A null is "not chosen": a workspace's zone is then its country's
-- and its formats the standard ones (DD_MMM_YYYY, H24); a person's is then the workspace's they are in.
ALTER TABLE app_lm_workspace
    ADD COLUMN time_zone   varchar(64),
    ADD COLUMN date_format varchar(16)
        CONSTRAINT app_lm_workspace_date_format_chk
            CHECK (date_format IN ('DD_MMM_YYYY', 'MMM_D_YYYY', 'DD_MM_YYYY', 'MM_DD_YYYY', 'YYYY_MM_DD')),
    ADD COLUMN time_format varchar(4)
        CONSTRAINT app_lm_workspace_time_format_chk CHECK (time_format IN ('H24', 'H12'));

ALTER TABLE app_lm_user
    ALTER COLUMN timezone DROP NOT NULL,
    ALTER COLUMN timezone DROP DEFAULT,
    ADD COLUMN date_format varchar(16)
        CONSTRAINT app_lm_user_date_format_chk
            CHECK (date_format IN ('DD_MMM_YYYY', 'MMM_D_YYYY', 'DD_MM_YYYY', 'MM_DD_YYYY', 'YYYY_MM_DD')),
    ADD COLUMN time_format varchar(4)
        CONSTRAINT app_lm_user_time_format_chk CHECK (time_format IN ('H24', 'H12'));

-- Nobody's clock moves on deploy. A workspace takes the zone most of its members already keep (V148 made that the
-- one their mailboxes send by); one with no active member is left to its country.
UPDATE app_lm_workspace w
SET time_zone = common.zone
FROM (
    SELECT m.workspace_id, mode() WITHIN GROUP (ORDER BY u.timezone) AS zone
    FROM app_lm_workspace_member m
    JOIN app_lm_user u ON u.id = m.user_id
    WHERE m.status = 'ACTIVE'
    GROUP BY m.workspace_id
) common
WHERE common.workspace_id = w.id;

-- A person follows the workspace only where that changes nothing: their zone is the zone of every workspace they are
-- an active member of. Anyone else keeps theirs as their own choice.
UPDATE app_lm_user u
SET timezone = NULL
WHERE EXISTS (SELECT 1 FROM app_lm_workspace_member m WHERE m.user_id = u.id AND m.status = 'ACTIVE')
  AND NOT EXISTS (
    SELECT 1
    FROM app_lm_workspace_member m
    JOIN app_lm_workspace w ON w.id = m.workspace_id
    WHERE m.user_id = u.id
      AND m.status = 'ACTIVE'
      AND w.time_zone IS DISTINCT FROM u.timezone
);
