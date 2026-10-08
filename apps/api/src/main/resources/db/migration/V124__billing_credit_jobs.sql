-- Billing's scheduled side (epic #734, #740).
--
-- A workspace's contact credits crossing 80%, 90% or used up is announced once per billing month: the row is the
-- claim, written under the ledger's balance lock, so concurrent spends crossing one threshold insert it once.
CREATE TABLE app_lm_credit_threshold_crossing (
    workspace_id  uuid         NOT NULL REFERENCES app_lm_workspace (id),
    month_start   timestamptz  NOT NULL,
    threshold     varchar(16)  NOT NULL
        CONSTRAINT app_lm_credit_threshold_crossing_threshold_chk CHECK (threshold IN ('EIGHTY', 'NINETY', 'OUT')),
    crossed_at    timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, month_start, threshold)
);

-- A job that must run once across instances claims its run here before acting.
CREATE TABLE app_lm_billing_job_run (
    job         varchar(48)  NOT NULL,
    run_key     varchar(64)  NOT NULL,
    claimed_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (job, run_key)
);

CREATE INDEX app_lm_credit_grant_plan_month_idx
    ON app_lm_credit_grant (workspace_id, expires_at) WHERE source = 'PLAN';
