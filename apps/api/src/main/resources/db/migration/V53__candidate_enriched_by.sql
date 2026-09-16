-- Which provider's research filled a candidate in.
--
-- The capture enrichment asks Bright Data's stored dataset first and falls back to a HarvestAPI live
-- scrape when that misses, is thin, or fails. The two cost roughly 4x apart, and until now the row
-- recorded only THAT it was researched (profile->>'enrichedAt'), never by whom — so a vendor going
-- slow silently moved the work, and the bill, onto the fallback with nothing to measure it by.
--
-- A column rather than a key in the `profile` jsonb because the question this answers is an
-- aggregate: how much of our enrichment came from where. That is a GROUP BY, not a field read back
-- with one profile.
--
-- Nullable with no default, and deliberately not a third value meaning "unknown": NULL is "nobody
-- researched this, or it was researched before this column existed", which is a different fact from
-- either vendor. Backfilling it into one of them would invent provenance nobody recorded, and the
-- counts this column exists to produce would be wrong from the first query.
--
-- Deliberately not added to app_lm_project_triage_company: company enrichment has exactly one
-- possible provider today, so recording it there would state nothing.

ALTER TABLE app_lm_project_candidate
    ADD COLUMN enriched_by varchar(16)
        CONSTRAINT app_lm_project_candidate_enriched_by_chk
            CHECK (enriched_by IN ('BRIGHTDATA', 'HARVESTAPI'));
