-- What a provider said about a LinkedIn company page, remembered once for everyone.
--
-- Every capture of a page the universe does not carry bills Bright Data, and nothing remembered the
-- answer: the same company captured in two mandates was bought twice. This table is that answer,
-- keyed on the slug the lookup was made with — the same reasoning V49 gives for app_lm_geocoded_place,
-- one layer up.
--
-- VENDOR-SOURCED ONLY, and that is a tenant boundary rather than tidiness. A hand-typed or
-- spreadsheet row is one firm's own research — the test fixture is literally
-- "Almarai (our own figures)" — and putting it in a table every workspace reads would leak it. What
-- goes here is a reproducible fact about a public page, with a key anyone can re-derive and a source
-- anyone can re-ask. Rows a person typed stay in app_lm_project_triage_company and nothing about them
-- changes.
--
-- Hence NO workspace_id, NO project_id, NO added_by. Who captured a company is in the audit trail,
-- which is where it belongs.
--
-- found = false is a stored miss. Without it, every slug the provider does not carry is re-bought on
-- every capture in every mandate, forever. fetched_at is what lets an answer age out
-- (lightmove.enrichment.company-cache-ttl).
--
-- Named app_lm_vendor_company and not app_lm_company on purpose: app_lm_companies (V3) is the retired
-- brightdata copy, still sitting in the schema and read-only since harden.sql. Two live company
-- tables one letter apart is a typo that silently answers from the wrong one.

CREATE TABLE app_lm_vendor_company (
    -- The lowercased /company/<slug> LinkedInUrls resolved. A numeric slug such as '79475146' is a
    -- real LinkedIn company id and belongs here; a /search/results/… URL resolves to null and never
    -- reaches this table, because there is nothing to key it on.
    linkedin_slug      text        PRIMARY KEY,

    -- Who answered last. One row per slug, not per (provider, slug): nothing compares two providers'
    -- accounts of one company, and a second row per company would only make "what do we know" a join.
    provider           text        NOT NULL,
    fetched_at         timestamptz NOT NULL,
    found              boolean     NOT NULL,

    company_name       text,

    -- The vendor's OWN leaf, in its own words, and its V2 id where V2 carries that name. This is the
    -- one place in the schema where industry_v2_* is a finer fact rather than V1 renamed: the
    -- universe never recorded a leaf, so an Apollo-backed row's V2 label is a rename (V63) and this
    -- one is not.
    industry_v2_code   integer,
    industry_v2_label  text,

    -- Derived from the line above by Industries.resolve, so a cached company groups with every other
    -- company in the report. NULL when nobody could resolve the vendor's words — unknown is an
    -- answer, and the FK below is why it cannot be the vendor's spelling instead.
    industry_v1        text REFERENCES app_lm_industry (v1_label),
    sector_group       text,

    company_country    text,
    company_city       text,

    -- NOT app_lm_apollo_companies.num_employees. This counts profiles claiming that employer;
    -- Apollo's is a headcount estimate. Two measurements, so two names — a union that banded them as
    -- one column would size the same company two ways and nobody would know why.
    employees_linkedin integer,

    website            text,
    linkedin_url       text,
    founded_year       integer,
    about              text,
    logo_url           text,

    -- The vendor's specialties, lower-cased, which is what app_lm_apollo_companies.keywords holds and
    -- what the market-segment filter matches on. Fetched and dropped on the floor until now.
    keywords           text[],

    -- What the provider actually returned. It is what makes the row re-derivable: the V2 to V1 map
    -- has 29 curated placements that are judgement, and when one is corrected the fix is a re-map
    -- from here rather than a second bill.
    raw                jsonb,

    created_at         timestamptz NOT NULL DEFAULT now(),

    -- A miss holds nothing but the fact that it was asked.
    CONSTRAINT app_lm_company_miss_chk CHECK (found OR company_name IS NULL)
);

COMMENT ON TABLE app_lm_vendor_company IS
    'Vendor company cache: one row per LinkedIn slug ever researched. Global, not tenant data; found = false is a stored miss.';
COMMENT ON COLUMN app_lm_vendor_company.industry_v2_label IS
    'The provider''s own V2 leaf. Unlike every other industry_v2_label in this schema, this is finer data and not V1 renamed.';
COMMENT ON COLUMN app_lm_vendor_company.employees_linkedin IS
    'Profiles claiming this employer on LinkedIn. Not the same measurement as app_lm_apollo_companies.num_employees.';
