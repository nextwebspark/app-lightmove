-- CoreSignal sourcing POC (experimental branch — never merges to main).
--
-- The Sourcing screen is re-backed by CoreSignal's Multi-source Company API: a search call turns a
-- project's Strategy scope into a revenue-desc-ordered list of CoreSignal company ids, then each id
-- is collected — a full company record, costing collect credits per record. Two tables:
--
--   1. app_lm_coresignal_company — the collect cache. One row per CoreSignal company ever collected,
--      keyed UNIQUE on coresignal_id. This is the POC's ONE safeguard: a company already here is
--      never collected (billed) again, by any project. Deliberately NOT workspace-scoped — a
--      CoreSignal record is the same facts for every tenant, the same cross-tenant stance as
--      app_lm_companies. Unlike app_lm_companies it is app-owned and app-written: the application
--      is the thing doing the collecting. The verbatim collect payload is kept as jsonb — it is the
--      POC's evidence (what does CoreSignal actually return?) and means a better field extraction
--      can be re-run without re-spending credits.
--
--   2. app_lm_coresignal_sourcing_run — one run row per project (UNIQUE project_id, replaced in
--      place when the strategy criteria change). Holds the search result: the ordered id list, the
--      provider's total-match count, how many of those ids this project has paid to collect so far
--      (requested_count), and the run lifecycle for the polling UI. Collected-so-far progress is
--      NOT stored — it is derived at read time from cache membership, so parallel collectors never
--      contend on this row.
--
-- Versioned V18: V16/V17 belong to a sibling branch; skipping their slots keeps a shared local
-- database bootable when switching between the branches.

CREATE TABLE app_lm_coresignal_company (
    id              uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    -- CoreSignal's own company id — the collect key, and the only identity the POC needs. Their id
    -- stability is undocumented; the raw payload keeps domain/linkedin as secondary identities if a
    -- real integration ever needs rebuild-stable keying like (source, source_id).
    coresignal_id   bigint       NOT NULL UNIQUE,
    name            text         NOT NULL,
    website         text,
    linkedin_url    text,
    description     text,
    industry        text,
    employees_count integer,
    size_range      text,
    revenue_annual  numeric,
    revenue_range   text,
    hq_location     text,
    hq_country      text,
    hq_country_iso2 varchar(2),
    founded_year    integer,
    logo_url        text,
    -- The collect response, verbatim. Re-extracting columns from here is free; re-collecting costs.
    payload         jsonb        NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

CREATE TABLE app_lm_coresignal_sourcing_run (
    id              uuid PRIMARY KEY     DEFAULT gen_random_uuid(),
    project_id      uuid         NOT NULL UNIQUE REFERENCES app_lm_project (id) ON DELETE CASCADE,
    status          varchar(16)  NOT NULL DEFAULT 'PENDING'
        CONSTRAINT app_lm_coresignal_run_status_chk
            CHECK (status IN ('PENDING', 'SEARCHING', 'COLLECTING', 'READY', 'FAILED')),
    -- SHA-256 hex of the resolved strategy criteria. A poll compares it against the strategy's
    -- current hash to tell the UI "these results are stale"; a re-run with an unchanged hash is
    -- answered from this row without touching the provider.
    criteria_hash   varchar(64)  NOT NULL,
    -- CoreSignal ids in the provider's revenue-desc order — fixed at search time, so results can
    -- stream into the UI as collects finish without ever reshuffling. jsonb rather than bigint[]:
    -- Hibernate maps List<Long> to JSON cleanly under the test profile's ddl-auto: validate.
    searched_ids    jsonb        NOT NULL DEFAULT '[]',
    -- The provider's x-total-results — how many companies matched, beyond the ids we kept.
    total_matched   bigint       NOT NULL DEFAULT 0,
    -- How deep into searched_ids this project has paid to collect (grows by one batch per extend).
    requested_count integer      NOT NULL,
    error_detail    text,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0
);

CREATE TRIGGER app_lm_coresignal_company_touch BEFORE UPDATE ON app_lm_coresignal_company
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
CREATE TRIGGER app_lm_coresignal_sourcing_run_touch BEFORE UPDATE ON app_lm_coresignal_sourcing_run
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
