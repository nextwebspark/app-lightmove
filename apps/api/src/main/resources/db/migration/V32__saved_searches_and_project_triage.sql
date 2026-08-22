-- Two tables the new Strategy screen needs and the old one had no use for.
--
-- app_lm_strategy_search — the toolbar's "Save Search". A mandate runs many searches over the same
-- universe before it settles on a scope, and losing the last one to the next chip click is the whole
-- complaint the button answers. The saved payload is the same jsonb document V30 put on
-- app_lm_strategy, for the same reasons: read whole, written whole, never queried by axis. Storing it
-- relationally here would mean four child tables holding a shape that already has a representation.
--
-- app_lm_project_triage_company — where "Add to Universe" lands, and what the triage screen reads. Strategy
-- searches a universe of 71,822 companies that belongs to no project; this table is the handful a
-- mandate has actually taken a position on, and the position is the row: in universe, shortlisted, or
-- declined. Triage never queries Apollo; it reads only these rows.

CREATE TABLE app_lm_strategy_search (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    name       varchar(120) NOT NULL,
    filter     jsonb        NOT NULL,
    -- Who saved it. Searches are shared within the mandate's team rather than private, so this is
    -- provenance for the list, not an ownership fence — a LEAD reworking a RESEARCHER's search is
    -- ordinary collaboration, and WORK_VIEW already decides who sees the mandate at all.
    created_by uuid         NOT NULL REFERENCES app_lm_user (id),
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    version    bigint       NOT NULL DEFAULT 0
);

-- Case-insensitive, so "GCC utilities" and "GCC Utilities" cannot both sit in one dropdown. Scoped to
-- the project: two mandates naming their searches the same thing is not a collision.
CREATE UNIQUE INDEX app_lm_strategy_search_name_uk
    ON app_lm_strategy_search (project_id, lower(name));

CREATE TRIGGER app_lm_strategy_search_touch BEFORE UPDATE ON app_lm_strategy_search
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

CREATE TABLE app_lm_project_triage_company (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    apollo_account_id text        NOT NULL,
    status            varchar(16) NOT NULL
        CONSTRAINT app_lm_project_triage_company_status_chk
        CHECK (status IN ('IN_UNIVERSE', 'SHORTLISTED', 'DECLINED')),
    -- The consultant's own note on this company for this mandate. Not Apollo's short_description,
    -- which the Strategy table already shows and which is the same sentence for every mandate.
    note              text,

    -- Write-time snapshot. Same contract as the off-limits list: a company a mandate has taken a
    -- position on must keep rendering after the pipeline stops publishing it, and a triage decision
    -- that silently loses its subject is worse than a stale row.
    company_name      text        NOT NULL,
    industry          text,
    company_country   text,
    company_city      text,
    num_employees     integer,
    annual_revenue    bigint,
    website           text,
    logo_url          text,

    added_by          uuid        NOT NULL REFERENCES app_lm_user (id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_project_triage_company IS
    'Companies a mandate has triaged out of the Apollo universe, with a write-time snapshot. One row per project-company decision.';

-- This is what makes "Add all to Universe" re-runnable: the bulk insert is ON CONFLICT DO NOTHING, so
-- widening a filter and adding again tops up the universe instead of failing or duplicating it. A row
-- already declined stays declined — re-adding must not quietly undo a triage decision.
CREATE UNIQUE INDEX app_lm_project_triage_company_uk
    ON app_lm_project_triage_company (project_id, apollo_account_id);

-- Triage's only read: one status within one project, plus its count for the sub-nav badge.
CREATE INDEX app_lm_project_triage_company_status_idx
    ON app_lm_project_triage_company (project_id, status);

-- Foreign-key indexes, the same pair V26 and V28 had to add after the fact for the earlier tables.
CREATE INDEX app_lm_project_triage_company_added_by_idx
    ON app_lm_project_triage_company (added_by);
CREATE INDEX app_lm_strategy_search_created_by_idx
    ON app_lm_strategy_search (created_by);

CREATE TRIGGER app_lm_project_triage_company_touch BEFORE UPDATE ON app_lm_project_triage_company
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
