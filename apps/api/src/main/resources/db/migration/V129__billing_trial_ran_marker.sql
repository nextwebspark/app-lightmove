-- #784: V128 took "has a period" to mark the trial that ran, but paying through Stripe or being invoiced gives a
-- founder's later workspace a period too, and the one-trial index then refused the payment's webhook. The trial that
-- ran now carries its own marker, which nothing after it clears.

ALTER TABLE app_lm_workspace_subscription
    ADD COLUMN trial_started_at timestamptz,
    ADD CONSTRAINT app_lm_workspace_subscription_trial_started_chk
        CHECK (trial_started_at IS NULL OR trial_started_by IS NOT NULL);

-- A founder's foundings were taken one at a time, so their first row is the trial that ran and every later one started
-- ended.
UPDATE app_lm_workspace_subscription SET trial_started_at = created_at
WHERE id IN (SELECT DISTINCT ON (trial_started_by) id
             FROM app_lm_workspace_subscription
             WHERE trial_started_by IS NOT NULL
             ORDER BY trial_started_by, created_at, id);

DROP INDEX app_lm_workspace_subscription_one_trial_uk;
CREATE UNIQUE INDEX app_lm_workspace_subscription_one_trial_uk
    ON app_lm_workspace_subscription (trial_started_by)
    WHERE trial_started_at IS NOT NULL;
