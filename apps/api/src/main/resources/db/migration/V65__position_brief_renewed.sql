-- The brief as the renewed Position screen draws it (#442): a location in two halves, a bonus that
-- may be a stated amount, and the split between the two competency panels.
--
-- Location was one line — "Riyadh, Saudi Arabia" — because the old mockup drew one box. The screen
-- now asks for a city and a country apart, the way every other place in the schema already stores
-- them (app_lm_project_candidate, app_lm_project_triage_company, app_lm_client), so the brief's
-- country is compared with theirs by spelling rather than by parsing a line.
--
-- The backfill reads the one line back. A stored line is one of three shapes. "city, country":
-- PositionDetails has only ever written a resolved catalog name after the comma, so the tail is the
-- country. A bare country: Position.forProject seeds the client's hq_country alone, and the
-- canonicaliser stores a lone country without a comma — SQL cannot consult the catalog, so a
-- comma-less value counts as a country only when some dedicated country column already holds that
-- exact spelling. Anything else is all city, and the next write canonicalises it.
--
-- bonus_value was numeric(6, 2) because a bonus was a percentage or a count of months. A bonus quoted
-- as "SAR 150,000" is neither, and the new FIXED_AMOUNT basis needs the column to hold money.
--
-- technical_share is how much of the assessment the technical panel carries, the behavioural panel
-- taking the rest. Seeded at an even split; the default goes again immediately (V40's idiom), so from
-- here on the column is always written explicitly.

ALTER TABLE app_lm_position ADD COLUMN location_city varchar(120);
ALTER TABLE app_lm_position ADD COLUMN location_country varchar(120);

UPDATE app_lm_position
SET location_country = NULLIF(trim(substring(location FROM '[^,]*$')), ''),
    location_city    = NULLIF(trim(regexp_replace(location, ',[^,]*$', '')), '')
WHERE location LIKE '%,%';

UPDATE app_lm_position
SET location_country = trim(location)
WHERE location NOT LIKE '%,%'
  AND trim(location) IN (
      SELECT hq_country FROM app_lm_client WHERE hq_country IS NOT NULL
      UNION
      SELECT location_country FROM app_lm_project_candidate WHERE location_country IS NOT NULL
      UNION
      SELECT company_country FROM app_lm_project_triage_company WHERE company_country IS NOT NULL);

UPDATE app_lm_position
SET location_city = NULLIF(trim(location), '')
WHERE location NOT LIKE '%,%'
  AND location_country IS NULL;

ALTER TABLE app_lm_position DROP COLUMN location;

ALTER TABLE app_lm_position ALTER COLUMN bonus_value TYPE numeric(14, 2);

ALTER TABLE app_lm_position
    ADD COLUMN technical_share integer NOT NULL DEFAULT 50
        CONSTRAINT app_lm_position_technical_share_chk CHECK (technical_share BETWEEN 0 AND 100);
ALTER TABLE app_lm_position ALTER COLUMN technical_share DROP DEFAULT;

COMMENT ON COLUMN app_lm_position.technical_share IS
    'Share of the assessment the technical competency panel carries, 0-100; the behavioural panel carries the rest.';
