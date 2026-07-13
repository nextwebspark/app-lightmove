-- The company universe: reference data, copied from the brightdata warehouse.
--
-- brightdata is a second database on the same Cloud SQL instance. It holds the scrape sources
-- (src_linkedin, src_zoominfo, supabase_company_dnb, …) and app_companies — a built projection over
-- them. That projection is what a consultant actually searches, so LightMove needs it locally: Postgres
-- cannot join across databases, and a company list that cannot be joined to a project is not a company
-- list. ops/cloudsql/sync-companies.sh does the copy.
--
-- ETL-owned, not app-owned. Rows arrive from the pipeline and the application only ever reads them —
-- ops/cloudsql/harden.sql reassigns this table to postgres and leaves lm_app with SELECT. Eventually the
-- pipeline retargets this database directly and the sync script retires; nothing about the shape below
-- changes when it does.
--
-- Columns mirror brightdata.app_companies verbatim, so the copy needs no mapping. Two are ours:
-- built_at is carried across (when the pipeline built the row), synced_at is when we last pulled it.

CREATE TABLE app_lm_companies (
    -- Ours, not the warehouse's. Upstream ids are `generated always as identity` and are re-minted every
    -- time the pipeline rebuilds its table — adopting them would mean that a rebuild silently repoints
    -- every project that had referenced a company. (source, source_id) below is the identity that
    -- actually survives a rebuild, and is what the sync matches on.
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    source           text NOT NULL DEFAULT 'brightdata',
    source_id        text NOT NULL,

    -- The warehouse's canonical company key. A pointer back into brightdata.companies, deliberately not
    -- a foreign key: that table stays behind, and this column has to survive without it.
    company_id       text,

    name             text NOT NULL,
    slogan           text,
    linkedin_url     text,
    website          text,
    domain           text,
    logo             text,

    primary_industry text,
    industry_tags    text[],
    sic_codes        text[],
    sic_labels       text[],
    specialties      text[],

    org_type         text,
    ownership        text,
    ipo_status       text,
    is_public        boolean,

    -- revenue_source / employee_source name the scrape the figure came from, and revenue_is_floor marks
    -- the ones we only know a lower bound for ("$1B+"). A number here is worth what its provenance is
    -- worth, so the provenance travels with it.
    revenue_usd      bigint,
    revenue_range    text,
    revenue_source   text,
    revenue_is_floor boolean,
    employee_count   integer,
    employee_range   text,
    employee_source  text,

    hq_country       text,
    hq_city          text,
    markets          text[],

    description      text,
    founded          integer,
    followers        integer,
    gd_rating        numeric,
    gd_reviews       integer,

    -- Pre-flattened haystack built by the pipeline. Unindexed for now: the search screen does not exist,
    -- and the index it wants (trigram? tsvector?) is a decision for whoever builds it.
    search_text      text,

    -- When the pipeline built the row. NOT defaulted to now(): stamping it with the sync time would
    -- overwrite the only signal we have about how stale the warehouse's own data is.
    built_at         timestamptz,
    -- When we last pulled it.
    synced_at        timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_companies IS
    'Company universe, copied from brightdata.app_companies by ops/cloudsql/sync-companies.sh. Read-only to the application.';

-- The identity that survives a pipeline rebuild, and the conflict target the sync upserts on. Upstream
-- enforces the same pair as unique; if it ever stopped being unique there, the sync would collapse rows
-- into each other, so assert it here too rather than trusting the warehouse.
CREATE UNIQUE INDEX app_lm_companies_source_uk ON app_lm_companies (source, source_id);

-- The filters the previous iteration of this product actually ran. The remaining upstream indexes
-- (employee_range, revenue_range, company_id) are left out until a screen asks for them — each one is
-- write amplification on every sync, and this instance is shared-core.
CREATE INDEX app_lm_companies_country_idx   ON app_lm_companies (hq_country);
CREATE INDEX app_lm_companies_industry_idx  ON app_lm_companies (primary_industry);
CREATE INDEX app_lm_companies_employees_idx ON app_lm_companies (employee_count);
CREATE INDEX app_lm_companies_revenue_idx   ON app_lm_companies (revenue_usd);
CREATE INDEX app_lm_companies_markets_idx   ON app_lm_companies USING gin (markets);
CREATE INDEX app_lm_companies_tags_idx      ON app_lm_companies USING gin (industry_tags);
