-- The three forms of an industry, beside the one a row already stores.
--
-- V61 settled which vocabulary is stored: the V1 label the universe publishes, because the Strategy
-- filter matches that column exactly. V62 published the vocabulary as data. Reading a row's V2 name
-- or its sector still meant a join, and the report is the one shape a join does not fit — it loads
-- whole rows into the JVM and tallies them there, so a per-row lookup would be a query per company.
--
-- Written only by Industries.resolve, through one method per table (TriageCompany.fileUnder,
-- StrategyCompanyRef.of, TriageCompanyWriter.rowPlaceholders). That single-writer rule is the whole
-- safety mechanism: denormalising is fine while there is exactly one place the values come from, and
-- two writers is how a row starts disagreeing with itself.
--
-- All three are nullable, and a null is not a gap to fill later. An industry nobody can resolve keeps
-- itself and leaves these empty — Industries' unknown-is-an-answer rule, inherited from Countries.
-- Deliberately no 'UNKNOWN' sector: "not resolvable" and "resolved to something" are different facts,
-- the way V56 keeps a null gender distinct from OTHER.
--
-- industry_v2_label on an Apollo-backed row is V1 renamed, not a finer fact. The universe never
-- recorded the leaf, so only a vendor-captured company states its own — a screen mixing the two
-- without saying which is which would reintroduce exactly the drift V61 closed, one layer up.

ALTER TABLE app_lm_project_triage_company
    ADD COLUMN industry_v2_code  integer,
    ADD COLUMN industry_v2_label text,
    ADD COLUMN sector_group      text;

ALTER TABLE app_lm_strategy_off_limits_company
    ADD COLUMN industry_v2_code  integer,
    ADD COLUMN industry_v2_label text,
    ADD COLUMN sector_group      text;

COMMENT ON COLUMN app_lm_project_triage_company.industry_v2_label IS
    'LinkedIn V2''s name for this industry. On an Apollo-backed row this is V1 renamed, not finer data.';

-- The catch-up for rows written before the columns existed. Joined rather than snapshotted inline,
-- unlike V61: app_lm_industry landed one migration ago and is the authority for exactly this.
UPDATE app_lm_project_triage_company AS target
SET industry_v2_code  = source.v2_code,
    industry_v2_label = source.v2_label,
    sector_group      = source.sector_group
FROM app_lm_industry AS source
WHERE target.industry = source.v1_label;

UPDATE app_lm_strategy_off_limits_company AS target
SET industry_v2_code  = source.v2_code,
    industry_v2_label = source.v2_label,
    sector_group      = source.sector_group
FROM app_lm_industry AS source
WHERE target.industry = source.v1_label;
