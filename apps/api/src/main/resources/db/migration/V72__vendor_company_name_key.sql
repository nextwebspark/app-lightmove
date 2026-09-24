-- The name a cached LinkedIn page is found by when the assistant asks for a company by name.
--
-- A live search accepts a page whose name, without its legal form, is the one asked for ("Majid Al
-- Futtaim LLC" answers "Majid Al Futtaim"). The cache compared the raw name, so the page it had just
-- paid for missed on the next ask and was bought again. name_key is the page's name the way the live
-- search reads it (CompanyNames.key, lower-cased, legal form dropped), written with every answer.
--
-- Not backfilled: the normalisation lives in Java, and a row written before this migration is found
-- by its raw name as before until it is next fetched.

ALTER TABLE app_lm_vendor_company ADD COLUMN name_key text;

CREATE INDEX app_lm_vendor_company_name_key_idx ON app_lm_vendor_company (name_key) WHERE found;
