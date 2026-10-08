-- Per-seat billing (epic #734, #744): a Stripe subscription whose quantity has yet to follow the workspace's staff.

ALTER TABLE app_lm_workspace_subscription
    -- Set by the membership change that moved the staff count, cleared once Stripe holds the new quantity; a sync
    -- that failed leaves it for the retry job.
    ADD COLUMN seat_sync_due_at  timestamptz;

CREATE INDEX app_lm_workspace_subscription_seat_sync_idx
    ON app_lm_workspace_subscription (seat_sync_due_at) WHERE seat_sync_due_at IS NOT NULL;
