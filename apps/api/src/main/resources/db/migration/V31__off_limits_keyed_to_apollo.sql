-- The off-limits list survives the Strategy makeover; its identity does not.
--
-- V14 keyed both company lists on (source, source_id) — the pair that survives a brightdata pipeline
-- rebuild. Apollo has no counterpart for it: its rows are keyed on apollo_account_id, which the
-- pipeline holds stable across exports. So a stored off-limits row points at a universe that is being
-- deleted, and re-keying it in place is not possible — there is no join that maps one identity onto
-- the other.
--
-- Dropped and recreated rather than altered, and the rows are lost. That is the honest outcome: a
-- best-effort re-match on company name would silently bar the wrong company from a mandate, which is
-- the one failure mode this list exists to prevent.
--
-- The snapshot columns stay, and for the same reason as V14: a barred company must keep rendering in
-- the panel after Apollo stops publishing it. They are Apollo's column names now, so the write path
-- copies fields across without a mapping table in between.
--
-- Still no foreign key to app_lm_apollo_companies. That table is ETL-owned — the pipeline truncates
-- and reloads it — and a mandate's exclusion list must not be something a reload can cascade away.

DROP TABLE app_lm_strategy_off_limits_company;

CREATE TABLE app_lm_strategy_off_limits_company (
    strategy_id       uuid    NOT NULL REFERENCES app_lm_strategy (id) ON DELETE CASCADE,
    sort_order        integer NOT NULL,
    apollo_account_id text    NOT NULL,
    -- Write-time snapshot of the company, so the list renders without the universe.
    company_name      text    NOT NULL,
    industry          text,
    company_city      text,
    company_country   text,
    logo_url          text,
    PRIMARY KEY (strategy_id, sort_order)
);

COMMENT ON TABLE app_lm_strategy_off_limits_company IS
    'Companies barred from a mandate. Keyed on apollo_account_id, with a write-time snapshot; deliberately not a foreign key.';

-- No unique index on (strategy_id, apollo_account_id), for the reason V11's header spells out:
-- Hibernate rewrites an @OrderColumn element collection in place when the list shrinks or reorders,
-- and a non-deferrable unique index fires on the transient mid-flush state. Duplicates are rejected
-- in StrategyService, where the whole submitted list is in hand at once.
