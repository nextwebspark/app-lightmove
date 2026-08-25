-- The Company Keywords box's vocabulary, materialised.
--
-- The box answers "which keywords start with or contain what I typed, and how much of the market does
-- each reach". Asked of app_lm_apollo_companies directly that is a seq scan over 71,822 rows expanded
-- through unnest into ~1.4M keyword rows and grouped into 765,169 buckets: ~200ms for a typed word,
-- ~670ms for one letter, ~790ms for none. LIMIT and HAVING apply after the grouping, so no parameter
-- the caller can send makes it cheaper — every accepted request costs the full scan. Against a pool of
-- ten connections on a shared instance that is a cheap request with an expensive answer, which is the
-- wrong shape to expose.
--
-- Materialised, the same question is a scan of one narrow 765,169-row relation: ~50ms, flat across
-- needles. No pg_trgm: a plain scan is already fast enough, and CREATE EXTENSION needs a privilege
-- lm_migrate is not guaranteed to hold.
--
-- REFRESH IS THE PIPELINE'S JOB. The universe is loaded out of band and this view does not follow it;
-- until refreshed it describes the load before last. ops/cloudsql/refresh-keyword-vocabulary.sh is the
-- command, and ops/dev/db.sh runs it after apollo-pull. A stale vocabulary offers a keyword that now
-- matches nothing, or hides one that would — the filter itself still reads the live table, so a stale
-- suggestion narrows honestly, it is only the offer that ages.

DO $$
BEGIN

IF to_regclass('public.app_lm_apollo_companies') IS NULL THEN
    RAISE NOTICE 'app_lm_apollo_companies absent — skipping the keyword vocabulary.';
    RETURN;
END IF;

CREATE MATERIALIZED VIEW app_lm_apollo_keywords AS
SELECT keyword, count(*)::int AS company_count
FROM app_lm_apollo_companies, unnest(keywords) AS keyword
WHERE keyword <> ''
GROUP BY 1;

COMMENT ON MATERIALIZED VIEW app_lm_apollo_keywords IS
    'Keyword vocabulary of the Apollo universe with the companies each reaches. Refreshed by the pipeline, never by the application.';

-- Unique so the refresh can run CONCURRENTLY and leave readers alone.
CREATE UNIQUE INDEX idx_lm_apollo_kwvocab      ON app_lm_apollo_keywords (keyword);
CREATE INDEX        idx_lm_apollo_kwvocab_size ON app_lm_apollo_keywords (company_count DESC);

END $$;
