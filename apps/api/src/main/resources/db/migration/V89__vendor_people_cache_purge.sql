-- The people cache is purged past lightmove.enrichment.people-cache-ttl rather than kept forever:
-- a row for someone nobody searches again would otherwise hold a third party's profile indefinitely.
-- These indexes make the purge a range delete.
CREATE INDEX app_lm_vendor_person_fetched_idx ON app_lm_vendor_person (fetched_at);
CREATE INDEX app_lm_vendor_people_search_fetched_idx ON app_lm_vendor_people_search (fetched_at);
