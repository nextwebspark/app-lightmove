-- A synthetic Apollo universe for the e2e database, so the Strategy cases (api/14, spa/strategy.mjs)
-- and anything that files a company by universe id do real work on a runner that cannot pull the
-- real one. Every expected value in those scripts is read back from this table at run time, so the
-- rows only have to be shaped like the market: the eight Location countries with a clear first and
-- second, employee and revenue figures across every band with some revenue unknown, V1 industry
-- labels the vocabulary resolves, keywords, and names carrying the LIKE metacharacters.
--
-- Idempotent, and a no-op on a table somebody has already loaded (the real universe, pulled with
-- `npm run dev:db:apollo`). Run before any facet read: the API caches the facet counts in memory.

DO $$
BEGIN
IF (SELECT count(*) FROM app_lm_apollo_companies) > 0 THEN
    RAISE NOTICE 'app_lm_apollo_companies already loaded — leaving it alone.';
    RETURN;
END IF;

WITH countries(position, name, city, share) AS (
    VALUES (0, 'United Arab Emirates', 'Dubai', 200), (1, 'Saudi Arabia', 'Riyadh', 130),
           (2, 'Qatar', 'Doha', 70), (3, 'Kuwait', 'Kuwait City', 50), (4, 'Oman', 'Muscat', 40),
           (5, 'Bahrain', 'Manama', 35), (6, 'Türkiye', 'Istanbul', 45), (7, 'Egypt', 'Cairo', 30)
),
expanded AS (
    SELECT c.name AS country, c.city, row_number() OVER () AS n
    FROM countries c, generate_series(1, c.share)
),
industries AS (
    SELECT array_agg(v1_label ORDER BY v1_label) AS labels FROM app_lm_industry
    WHERE v1_label IN ('banking', 'financial services', 'oil & energy', 'information technology & services',
                       'computer software', 'real estate', 'construction', 'hospitality',
                       'airlines/aviation', 'retail', 'telecommunications', 'logistics & supply chain')
),
vocabulary(words) AS (
    VALUES (ARRAY['b2b', 'saas', 'fintech', 'payments', 'cloud', 'logistics', 'energy transition',
                  'hydrogen', 'real estate development', 'hospitality', 'e-commerce', 'cybersecurity'])
)
INSERT INTO app_lm_apollo_companies (
    apollo_account_id, company_name, num_employees, industry, keywords, website, company_linkedin_url,
    company_city, company_country, annual_revenue, founded_year, short_description, row_hash, source_file)
SELECT
    'e2e' || lpad(e.n::text, 6, '0'),
    CASE
        WHEN e.n % 97 = 0 THEN 'Hundred ' || e.n || '% Holdings'
        WHEN e.n % 89 = 0 THEN 'Under_' || e.n || ' Group'
        ELSE (ARRAY['Al Noor', 'Gulf', 'Desert', 'Falcon', 'Pearl', 'Oasis', 'Crescent', 'Summit',
                    'Meridian', 'Horizon', 'Dune', 'Harbour'])[1 + e.n % 12]
             || ' ' || (ARRAY['Capital', 'Energy', 'Systems', 'Logistics', 'Properties', 'Telecom',
                              'Hotels', 'Airways', 'Trading', 'Ventures', 'Retail'])[1 + (e.n / 12) % 11]
             || ' ' || e.n
    END,
    (ARRAY[8, 35, 120, 380, 750, 1200, 1550, 1900, 4200, 12000, 60000])[1 + (e.n * 7) % 11] + e.n % 17,
    CASE WHEN e.n % 41 = 0 THEN NULL ELSE i.labels[1 + e.n % array_length(i.labels, 1)] END,
    ARRAY[v.words[1 + e.n % 12], v.words[1 + (e.n * 5 + 3) % 12]],
    'https://www.e2e-company-' || e.n || '.example',
    'https://www.linkedin.com/company/e2e-company-' || e.n,
    e.city,
    e.country,
    CASE WHEN e.n % 6 = 0 THEN NULL
         ELSE (ARRAY[800000, 4000000, 25000000, 120000000, 600000000, 3000000000, 15000000000])[1 + (e.n * 3) % 7]::bigint
    END,
    (1950 + e.n % 70)::smallint,
    'A synthetic company for the e2e matrix.',
    md5(e.n::text),
    'e2e/stack/seed-universe.sql'
FROM expanded e, industries i, vocabulary v;

REFRESH MATERIALIZED VIEW app_lm_apollo_keywords;
END $$;
