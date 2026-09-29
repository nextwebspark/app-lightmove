-- What Bright Data said about a person, remembered once for everyone — V64's reasoning, one table over.
--
-- Every record the people dataset returns is billed, and nothing remembered it: the same executive
-- found by a second Find executives run, by a run in another mandate, or captured with the plugin
-- after a run found them was bought again. This is that record, keyed on the profile slug.
--
-- VENDOR-SOURCED ONLY, and that is the tenant boundary V64 draws: a hit is a reproducible fact about a
-- public page. NO workspace_id, NO project_id, NO added_by — who filed a person is the mandate's row
-- and the audit trail. No stored misses either: a person is only ever written because a search or a
-- lookup returned them.
CREATE TABLE app_lm_vendor_person (
    -- The lowercased /in/<slug> — the dataset's own linkedin_id.
    linkedin_slug        text        PRIMARY KEY,
    provider             text        NOT NULL,
    fetched_at           timestamptz NOT NULL,

    -- The three things a cached search narrows by, lifted out of `raw` so the narrowing is an index read.
    current_company_slug text,
    country_code         text,
    position             text,

    -- The whole hit as the vendor returned it: the profile is rebuilt from this, never re-bought.
    raw                  jsonb       NOT NULL,

    created_at           timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX app_lm_vendor_person_company_idx
    ON app_lm_vendor_person (current_company_slug, fetched_at DESC);

COMMENT ON TABLE app_lm_vendor_person IS
    'Vendor people cache: one row per LinkedIn profile slug ever returned by a billed search or lookup. Global, not tenant data.';

-- One row per people search asked: the question, and who came back. The same question inside
-- lightmove.enrichment.people-cache-ttl is answered from here and app_lm_vendor_person with no vendor
-- call at all. `query_key` is a hash of the company slug, the title words, the countries and the size,
-- each normalised, so the same question spelt in another order is the same row.
CREATE TABLE app_lm_vendor_people_search (
    query_key    text        PRIMARY KEY,
    company_slug text        NOT NULL,
    slugs        text[]      NOT NULL,
    total_hits   bigint,
    fetched_at   timestamptz NOT NULL
);

COMMENT ON TABLE app_lm_vendor_people_search IS
    'Vendor people-search cache: which profiles one normalised search returned. Global, not tenant data.';

-- A run now says what it paid for and what it read back for free.
ALTER TABLE app_lm_executive_sourcing_run
    ADD COLUMN cached_hits integer NOT NULL DEFAULT 0;
