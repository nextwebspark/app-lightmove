-- ── What a firm has spent today ───────────────────────────────────────────────
--
-- The first ceiling in this application that actually holds across instances.
--
-- LlmBudgetGuard counts per user per minute over Bucket4jRateLimiter, which lives in one JVM's heap.
-- On --max-instances 2 that ceiling is silently doubled, and it is keyed by the person rather than
-- by the firm, so it cannot answer "what has this workspace spent". Fine as a brake on a double
-- click; not fine as the thing standing between a firm and a Vertex bill. #430 reaches the same
-- verdict; this is that counter, built here because AI Research is the first button that needs it.
--
-- Deliberately generic. `meter` names the workload rather than a column per feature, so the assistant's
-- own ceiling is a new value and not a new table.
--
-- `spend_date` is written as (now() AT TIME ZONE 'UTC')::date by the one statement that touches this
-- table, never current_date: two instances in two session timezones would otherwise disagree about
-- when the day turned, and the disagreement would show up as a firm getting two days' budget.

CREATE TABLE app_lm_workspace_daily_spend (
    workspace_id uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    meter        varchar(32) NOT NULL,
    spend_date   date        NOT NULL,
    calls        integer     NOT NULL DEFAULT 0 CHECK (calls >= 0),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, meter, spend_date)
);

COMMENT ON TABLE app_lm_workspace_daily_spend IS
    'One row per workspace, meter and UTC day: how many billed calls that firm has made. The ceiling is configuration, not a column — a limit lowered mid-day must bite immediately rather than be remembered as yesterday''s.';
COMMENT ON COLUMN app_lm_workspace_daily_spend.meter IS
    'The workload being capped, e.g. company-discovery. A second capped workload is a new value here.';
COMMENT ON COLUMN app_lm_workspace_daily_spend.calls IS
    'Billed calls, not tokens. A coarse brake; the per-turn token columns on app_lm_assistant_turn are the meter.';
