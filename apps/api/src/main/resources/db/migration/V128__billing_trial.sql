-- The trial (epic #734, #771): a workspace founded from now on is on Pro for a while, with a few contact credits and
-- no card, and its paid features lock once the trial ends unless a plan is bought.

ALTER TABLE app_lm_workspace_subscription
    -- Set on a trial the app started; Stripe's own trialing status never writes it.
    ADD COLUMN trial_ends_at     timestamptz,
    -- Who founded the workspace the trial came with: one trial per person, so a second founding starts ended.
    ADD COLUMN trial_started_by  uuid REFERENCES app_lm_user (id);

CREATE INDEX app_lm_workspace_subscription_trial_by_idx
    ON app_lm_workspace_subscription (trial_started_by) WHERE trial_started_by IS NOT NULL;

CREATE INDEX app_lm_workspace_subscription_trial_end_idx
    ON app_lm_workspace_subscription (trial_ends_at)
    WHERE status = 'TRIALING' AND stripe_subscription_id IS NULL;

ALTER TABLE app_lm_billing_notice DROP CONSTRAINT app_lm_billing_notice_kind_chk;
ALTER TABLE app_lm_billing_notice ADD CONSTRAINT app_lm_billing_notice_kind_chk
    CHECK (kind IN ('PAYMENT_FAILED', 'PURCHASED_CREDITS_EXPIRING', 'TRIAL_ENDING', 'TRIAL_ENDED'));
