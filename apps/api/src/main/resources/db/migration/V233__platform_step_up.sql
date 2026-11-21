-- A super admin's last fresh two-factor code (#1313): a high-impact platform action asks for one entered within the
-- step-up window. One row per person, overwritten by each right code. Account data, as the factor is: no workspace.

CREATE TABLE app_lm_platform_step_up (
    user_id     uuid        PRIMARY KEY REFERENCES app_lm_user (id) ON DELETE CASCADE,
    verified_at timestamptz NOT NULL
);

COMMENT ON TABLE app_lm_platform_step_up IS
    'When a platform admin last entered a two-factor code for a high-impact action. Read by user_id, never by workspace.';
