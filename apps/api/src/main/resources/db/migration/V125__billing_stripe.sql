-- Stripe (epic #734, #743): each workspace's Stripe customer, the webhook events already handled, and what a
-- subscription needs to follow Stripe's word on it.

-- One Stripe customer per workspace, made the first time an admin opens checkout or the portal, and never two:
-- every webhook names its customer, and this is how it is turned back into a workspace.
CREATE TABLE app_lm_billing_customer (
    workspace_id        uuid         PRIMARY KEY REFERENCES app_lm_workspace (id),
    stripe_customer_id  varchar(64)  NOT NULL
        CONSTRAINT app_lm_billing_customer_stripe_uk UNIQUE,
    created_at          timestamptz  NOT NULL DEFAULT now()
);

-- A Stripe event handled, claimed in the transaction that handles it: a replay finds its row and changes nothing,
-- and a handler that fails rolls its claim back so Stripe's retry runs it again.
CREATE TABLE app_lm_billing_webhook_event (
    event_id     varchar(255)  PRIMARY KEY,
    type         varchar(64)   NOT NULL,
    received_at  timestamptz   NOT NULL DEFAULT now()
);

ALTER TABLE app_lm_workspace_subscription
    -- When a payment first failed; past lightmove.billing.past-due-grace the month's credits stop being granted.
    ADD COLUMN past_due_since    timestamptz,
    -- When the Stripe event last applied to the row was created, so one delivered late never undoes a newer one.
    ADD COLUMN stripe_synced_at  timestamptz,
    ADD CONSTRAINT app_lm_workspace_subscription_past_due_chk
        CHECK (past_due_since IS NULL OR status = 'PAST_DUE');

CREATE UNIQUE INDEX app_lm_workspace_subscription_stripe_uk
    ON app_lm_workspace_subscription (stripe_subscription_id) WHERE stripe_subscription_id IS NOT NULL;
