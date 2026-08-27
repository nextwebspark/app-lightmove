-- The position lock is retired. A brief was frozen once it passed a readiness gate (both competency
-- panels at exactly 100%, at least one required criterion), and only a project lead could reopen it.
-- The mandate never needed a frozen benchmark, so the gate, its action and its columns all go.

ALTER TABLE app_lm_position
    DROP COLUMN locked_at,
    DROP COLUMN locked_by;

-- app_lm_role_action.action_id is ON DELETE CASCADE, so the LEAD grant V19 gave this action goes with
-- the catalog row. Nothing else references it: an action is only ever named through app_lm_role_action.
DELETE FROM app_lm_action WHERE scope = 'PROJECT' AND name = 'POSITION_UNLOCK';
