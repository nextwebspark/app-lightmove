-- One spelling per country in the columns a mandate's own rows carry.
--
-- Until now a country was normalised on the way out and never on the way in, so six write paths put
-- six spellings into columns that are matched exactly: the Strategy filter runs
-- `company_country IN (:countries)` with no lower(), the report groups by the raw column, and the
-- map's country branch is whatever string arrived. Bright Data's person enricher fell back to the
-- profile's ISO code, which is how "AE" and "SA" came to sit beside "United Arab Emirates" and
-- "Saudi Arabia" in one column, splitting one country into two groups on every screen that counts.
--
-- `Countries` now canonicalises at every door, so this is the one-off catch-up for rows written
-- before it existed. The spellings below are a snapshot of that catalog, the way V42 snapshotted the
-- template library: the catalog stays the authority from here, and a migration is immutable.
--
-- Only the spellings this schema could plausibly hold are listed — the bare alpha-2 codes of the
-- markets the universe carries, plus the abbreviations a researcher types. A row whose country is
-- already canonical, or is a country nobody can resolve, is left exactly as it is: an unresolvable
-- spelling is a fact somebody entered, not a row to guess at.

--
-- One statement, and deliberately no temp table: `harden.sql` revokes ALL on the database from PUBLIC,
-- which takes the TEMPORARY privilege with it, and nothing grants it back to lm_migrate. A temp table
-- here would pass every local run — Testcontainers connects as a superuser — and fail the deploy step
-- that runs migrations in production. A data-modifying CTE needs no privilege at all.

WITH spelling (typed, canonical) AS (VALUES
    ('ae', 'United Arab Emirates'),
    ('uae', 'United Arab Emirates'),
    ('u.a.e.', 'United Arab Emirates'),
    ('u.a.e', 'United Arab Emirates'),
    ('emirates', 'United Arab Emirates'),
    ('sa', 'Saudi Arabia'),
    ('ksa', 'Saudi Arabia'),
    ('k.s.a.', 'Saudi Arabia'),
    ('k.s.a', 'Saudi Arabia'),
    ('saudi', 'Saudi Arabia'),
    ('kingdom of saudi arabia', 'Saudi Arabia'),
    ('qa', 'Qatar'),
    ('state of qatar', 'Qatar'),
    ('kw', 'Kuwait'),
    ('state of kuwait', 'Kuwait'),
    ('om', 'Oman'),
    ('sultanate of oman', 'Oman'),
    ('bh', 'Bahrain'),
    ('bahrein', 'Bahrain'),
    ('kingdom of bahrain', 'Bahrain'),
    ('eg', 'Egypt'),
    ('tr', 'Türkiye'),
    ('turkey', 'Türkiye'),
    ('turkiye', 'Türkiye'),
    ('us', 'United States'),
    ('usa', 'United States'),
    ('u.s.a.', 'United States'),
    ('united states of america', 'United States'),
    ('gb', 'United Kingdom'),
    ('uk', 'United Kingdom'),
    ('great britain', 'United Kingdom'),
    ('britain', 'United Kingdom')
),
triaged AS (
    UPDATE app_lm_project_triage_company AS target
    SET company_country = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.company_country)) = spelling.typed
      AND target.company_country <> spelling.canonical
    RETURNING 1
),
mapped AS (
    UPDATE app_lm_project_candidate AS target
    SET location_country = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.location_country)) = spelling.typed
      AND target.location_country <> spelling.canonical
    RETURNING 1
),
clients AS (
    UPDATE app_lm_client AS target
    SET hq_country = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.hq_country)) = spelling.typed
      AND target.hq_country <> spelling.canonical
    RETURNING 1
),
off_limits AS (
    UPDATE app_lm_strategy_off_limits_company AS target
    SET company_country = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.company_country)) = spelling.typed
      AND target.company_country <> spelling.canonical
    RETURNING 1
)
SELECT count(*) FROM (
    SELECT 1 FROM triaged
    UNION ALL SELECT 1 FROM mapped
    UNION ALL SELECT 1 FROM clients
    UNION ALL SELECT 1 FROM off_limits
) AS rewritten;


-- The same catch-up for the cities the catalog folds. Without it a column would hold "khobar" on
-- every row written before this shipped and "Al Khobar" on every row after — one city, two groups,
-- which is the split this whole change exists to end.
WITH spelling (typed, canonical) AS (VALUES
    ('al kuwayt', 'Kuwait City'),
    ('ar rayyan', 'Al Rayyan'),
    ('ar rifa', 'Riffa'),
    ('khobar', 'Al Khobar'),
    ('mecca', 'Makkah'),
    ('medina', 'Madinah'),
    ('seeb', 'Al Seeb')
),
triaged_city AS (
    UPDATE app_lm_project_triage_company AS target
    SET company_city = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.company_city)) = spelling.typed
      AND target.company_city <> spelling.canonical
    RETURNING 1
),
mapped_city AS (
    UPDATE app_lm_project_candidate AS target
    SET location_city = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.location_city)) = spelling.typed
      AND target.location_city <> spelling.canonical
    RETURNING 1
),
client_city AS (
    UPDATE app_lm_client AS target
    SET hq_city = spelling.canonical
    FROM spelling
    WHERE lower(btrim(target.hq_city)) = spelling.typed
      AND target.hq_city <> spelling.canonical
    RETURNING 1
)
SELECT count(*) FROM (
    SELECT 1 FROM triaged_city
    UNION ALL SELECT 1 FROM mapped_city
    UNION ALL SELECT 1 FROM client_city
) AS rewritten_cities;
