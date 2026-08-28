-- Strategic priorities stop being a closed vocabulary.
--
-- V39 stored the priorities as a set keyed on the value, because the screen offered a fixed row of
-- five and each was on or off. That was the wrong shape for the question: a mandate's strategic
-- alignment is whatever the client said it was, and an enum can only ever offer what somebody
-- anticipated. They become an ordered list of free text the consultant writes, the same idiom as the
-- key responsibilities of step one — one child table replaced wholesale by its step's write.
--
-- The five values already stored convert to the labels the screen showed for them, so a brief that
-- selected "Capital discipline" still reads "Capital discipline" afterwards. sort_order is assigned
-- alphabetically: a set has no order to preserve, and any deterministic one beats leaving the list to
-- come back in whatever order the heap hands over.

ALTER TABLE app_lm_position_priority DROP CONSTRAINT app_lm_position_priority_pkey;

ALTER TABLE app_lm_position_priority ALTER COLUMN priority TYPE varchar(120);

UPDATE app_lm_position_priority
SET priority = CASE priority
        WHEN 'CAPITAL_DISCIPLINE'      THEN 'Capital discipline'
        WHEN 'PORTFOLIO_GROWTH'        THEN 'Portfolio growth'
        WHEN 'OPERATIONAL_EXCELLENCE'  THEN 'Operational excellence'
        WHEN 'GOVERNANCE_AND_CONTROLS' THEN 'Governance & controls'
        WHEN 'TALENT_DEVELOPMENT'      THEN 'Talent development'
        ELSE priority
    END;

ALTER TABLE app_lm_position_priority ADD COLUMN sort_order integer;

UPDATE app_lm_position_priority target
SET sort_order = ordered.slot
FROM (SELECT position_id,
             priority,
             row_number() OVER (PARTITION BY position_id ORDER BY priority) - 1 AS slot
      FROM app_lm_position_priority) AS ordered
WHERE target.position_id = ordered.position_id
  AND target.priority = ordered.priority;

ALTER TABLE app_lm_position_priority ALTER COLUMN sort_order SET NOT NULL;

ALTER TABLE app_lm_position_priority
    ADD CONSTRAINT app_lm_position_priority_pkey PRIMARY KEY (position_id, sort_order);

COMMENT ON TABLE app_lm_position_priority IS
    'The strategic priorities this mandate is said to serve, in the order the brief lists them.';
