-- Billing's emails to a workspace's billing managers (epic #734, #745).
--
-- A payment failing and bought credits about to expire are each announced once: the row is the claim, committed
-- before any email goes, so of two deliveries or two instances only one sends. The 80/90/100% emails need none of
-- this — V124's threshold crossing is already their claim.
CREATE TABLE app_lm_billing_notice (
    kind          varchar(32)   NOT NULL
        CONSTRAINT app_lm_billing_notice_kind_chk CHECK (kind IN ('PAYMENT_FAILED', 'PURCHASED_CREDITS_EXPIRING')),
    subject_ref   varchar(128)  NOT NULL,
    workspace_id  uuid          NOT NULL REFERENCES app_lm_workspace (id),
    claimed_at    timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, subject_ref)
);

CREATE INDEX app_lm_credit_grant_purchased_expiry_idx
    ON app_lm_credit_grant (expires_at) WHERE source = 'PURCHASED' AND remaining > 0;
