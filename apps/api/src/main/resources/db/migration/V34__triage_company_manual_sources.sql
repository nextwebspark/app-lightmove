-- A mandate's universe stops being "whatever Apollo publishes".
--
-- V32 built app_lm_project_triage_company around one way in: Strategy filters the Apollo universe and
-- "Add to Universe" snapshots the row. That made apollo_account_id NOT NULL, which in turn made the
-- other two ways a consultant actually finds a company unrepresentable — typing one in by hand, and
-- capturing one off a LinkedIn or corporate page with the browser plugin. Neither has a universe id,
-- and minting a synthetic one would let a hand-typed row collide with a real Apollo account.
--
-- So the id becomes optional and `source` records which door the company came through. That is
-- provenance the screen shows rather than bookkeeping: a headcount typed by a researcher and one
-- exported by Apollo are not equally trustworthy, and a consultant reading the grid should be able to
-- see which is which.
--
-- Nothing here touches app_lm_apollo_companies. It is ETL-owned, lm_app holds SELECT on it and
-- nothing else, and a mandate deleting its own triage row leaves the company in the universe for
-- every other mandate.

ALTER TABLE app_lm_project_triage_company
    ALTER COLUMN apollo_account_id DROP NOT NULL,
    ADD COLUMN source               varchar(16) NOT NULL DEFAULT 'STRATEGY',
    ADD COLUMN company_linkedin_url text,
    -- integer rather than the smallint the Apollo export uses for the same fact: a year is naturally an
    -- int, and the narrower type there bought nothing but a cast that fails (see CompanyRow.foundedYear).
    ADD COLUMN founded_year         integer,
    ADD COLUMN short_description    text,
    -- The page the plugin captured from. Null for every row that did not come through it.
    ADD COLUMN source_url           text;

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_source_chk
        CHECK (source IN ('STRATEGY', 'MANUAL', 'EXTENSION')),
    -- A STRATEGY row is by definition one taken out of the universe, so it must carry the id it was
    -- taken by. Without this, a capture that forgot to set its source would file itself as a market
    -- company with nothing to point back at.
    ADD CONSTRAINT app_lm_project_triage_company_apollo_source_chk
        CHECK (source <> 'STRATEGY' OR apollo_account_id IS NOT NULL);

COMMENT ON COLUMN app_lm_project_triage_company.source IS
    'Which door the company came through: STRATEGY (Apollo universe), MANUAL (typed in), EXTENSION (browser plugin).';

-- app_lm_project_triage_company_uk (project_id, apollo_account_id) is deliberately left alone. Postgres
-- treats NULLs as distinct, so manual rows simply do not meet each other through it and the bulk add's
-- ON CONFLICT DO NOTHING stays re-runnable exactly as V32 describes.
--
-- Which leaves manual rows with no duplicate guard of their own, and they need one: the whole point of
-- typing a company in is that nothing else identifies it, so the name is all there is to collide on.
-- Scoped to the project, because two mandates tracking the same company is not a duplicate.
CREATE UNIQUE INDEX app_lm_project_triage_company_manual_name_uk
    ON app_lm_project_triage_company (project_id, lower(company_name))
    WHERE apollo_account_id IS NULL;
