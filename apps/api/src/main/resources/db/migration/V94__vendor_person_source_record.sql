-- The provider's own record, untouched, beside `raw` — which stays in Bright Data's shape because every
-- reader of the people cache binds it as one. ContactOut answers with far more than that shape holds
-- (headline, links, certifications, contact availability, the employer's facts), and a field shown
-- later should not have to be bought again. Null for a Bright Data row, whose `raw` is already its own.
-- It ages out with the row, under the same people-cache TTL.
ALTER TABLE app_lm_vendor_person
    ADD COLUMN source_record jsonb;
