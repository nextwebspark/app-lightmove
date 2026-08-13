-- Drop the mandate_reason CHECK added in V7 so the enum can grow (adding GROWTH_EXPANSION to match
-- the mockup's fifth option) without another migration every time. Same move as V10 made for
-- employment_type: the value set is owned by the MandateReason Java enum (@Enumerated(STRING)) alone.

ALTER TABLE app_lm_position DROP CONSTRAINT app_lm_position_reason_chk;
