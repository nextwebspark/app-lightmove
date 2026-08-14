-- The Apollo company universe: a second body of reference data, backing the mandate report.
--
-- ETL-owned like app_lm_companies — the pipeline writes it, the application only ever reads. It is a
-- separate table rather than more rows in app_lm_companies because it agrees with that table on almost
-- nothing: industries are lower-cased and drawn from a 148-label vocabulary rather than 523, countries
-- are spelled out ("United Arab Emirates") where the other holds ISO codes ("AE"), size arrives as raw
-- num_employees / annual_revenue rather than the warehouse's pre-bucketed range strings, and the
-- (source, source_id) identity that every strategy company list stores has no counterpart here at all.
-- Folding the two together would mean picking a winner for each of those and silently rewriting one
-- side's data to suit the other.
--
-- This table already exists in the deployed database, created out of band by the pipeline ahead of
-- this migration, and it is owned there by the human who loaded it rather than by lm_app. So the whole
-- body is guarded by one existence check and skipped wholesale when the table is already present.
--
-- Per-statement IF NOT EXISTS is what this originally used, and it does not work: Postgres resolves
-- and ownership-checks the target relation *before* it decides to skip, so COMMENT ON and every
-- CREATE INDEX IF NOT EXISTS failed with "must be owner of table app_lm_apollo_companies" when Flyway
-- connected as lm_app. Idempotency at the statement level is not idempotency at the privilege level.
-- Guarding the block sidesteps both: nothing is attempted, so nothing needs ownership.
--
-- On the deployed database this is therefore a pure no-op that records the version. On a fresh clone
-- or a Testcontainers database it creates the table, and the migrating role owns what it created, so
-- the comment and indexes apply. The definition below is generated from the live table, column for
-- column, so the two cannot disagree.
--
-- Nothing in this file drops, alters or truncates: the rows already loaded are untouched.

DO $$
BEGIN

IF to_regclass('public.app_lm_apollo_companies') IS NOT NULL THEN
    RAISE NOTICE 'app_lm_apollo_companies already present — leaving it and its data untouched.';
    RETURN;
END IF;

CREATE TABLE app_lm_apollo_companies (
    -- Apollo's own account id, used verbatim as the key. Unlike the brightdata warehouse — whose ids
    -- are re-minted on every rebuild, which is why app_lm_companies mints its own — this one is stable
    -- across exports, and it is the column the loader upserts on.
    apollo_account_id                    text PRIMARY KEY,

    company_name                         text NOT NULL,
    company_name_for_emails              text,
    account_stage                        text,
    lists                                text,
    account_owner                        text,

    num_employees                        integer,
    industry                             text,
    keywords                             text[],
    technologies                         text[],

    website                              text,
    company_linkedin_url                 text,
    facebook_url                         text,
    twitter_url                          text,
    company_phone                        text,

    company_street                       text,
    company_city                         text,
    company_state                        text,
    company_country                      text,
    company_postal_code                  text,
    company_address                      text,

    total_funding                        bigint,
    latest_funding                       text,
    latest_funding_amount                bigint,
    last_raised_at                       date,
    -- The only revenue figure Apollo carries, and it is set on roughly one row in ten. A report that
    -- filters on a revenue band therefore has to say what it excluded — see ReportService.
    annual_revenue                       bigint,

    number_of_retail_locations           integer,
    apollo_record_id                     text,
    sic_codes                            text[],
    naics_codes                          text[],
    short_description                    text,
    founded_year                         smallint,
    logo_url                             text,
    parent_company                       text,
    parent_company_org_id                text,

    primary_intent_topic                 text,
    primary_intent_score                 numeric,
    secondary_intent_topic               text,
    secondary_intent_score               numeric,
    prereq_research_target_company       text,
    qualify_account                      text,
    prereq_determine_research_guidelines text,

    -- The loader's change detector: a digest of the source row, so an unchanged export is a no-op.
    row_hash                             text NOT NULL,
    source_file                          text,
    first_seen_at                        timestamptz NOT NULL DEFAULT now(),
    updated_at                           timestamptz NOT NULL DEFAULT now()
);

COMMENT ON TABLE app_lm_apollo_companies IS
    'Apollo company universe, loaded by the pipeline. Read-only to the application; backs the mandate report.';

-- The filters the report actually runs: sector, country and headcount narrow the scope, and keywords
-- carries the inferred-tag match. technologies and updated_at are the pipeline's own.
CREATE INDEX idx_lm_apollo_country  ON app_lm_apollo_companies (company_country);
CREATE INDEX idx_lm_apollo_industry ON app_lm_apollo_companies (industry);
CREATE INDEX idx_lm_apollo_emp      ON app_lm_apollo_companies (num_employees);
CREATE INDEX idx_lm_apollo_updated  ON app_lm_apollo_companies (updated_at);
CREATE INDEX idx_lm_apollo_kw       ON app_lm_apollo_companies USING gin (keywords);
CREATE INDEX idx_lm_apollo_tech     ON app_lm_apollo_companies USING gin (technologies);

END $$;
