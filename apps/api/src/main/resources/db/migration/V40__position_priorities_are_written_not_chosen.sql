-- Strategic priorities stop being a closed vocabulary.
--
-- V39 stored them as a set keyed on the enum value, because the screen offered a fixed row of five
-- and each was on or off. That was the wrong shape for the question: a mandate's strategic alignment
-- is whatever the client said it was, and an enum can only ever offer what somebody anticipated.
-- A priority becomes a row a consultant can write, delete, and switch on or off — a named chip
-- rather than a member of a vocabulary.
--
-- The conversion writes out what the old screen actually drew. It always showed all five chips with
-- some of them lit, so every position gets all five as rows, and the ones it had stored — the lit
-- ones — are the ones marked selected. Nothing anybody chose changes state, and no brief opens on an
-- empty rail where it used to show a palette.

ALTER TABLE app_lm_position_priority DROP CONSTRAINT app_lm_position_priority_pkey;

ALTER TABLE app_lm_position_priority ALTER COLUMN priority TYPE varchar(120);

-- A stored row was a chosen priority, so every one of them converts as selected. The default goes
-- again immediately: from here on the column is always written explicitly.
ALTER TABLE app_lm_position_priority
    ADD COLUMN selected boolean NOT NULL DEFAULT true;
ALTER TABLE app_lm_position_priority ALTER COLUMN selected DROP DEFAULT;

UPDATE app_lm_position_priority
SET priority = CASE priority
        WHEN 'CAPITAL_DISCIPLINE'      THEN 'Capital discipline'
        WHEN 'PORTFOLIO_GROWTH'        THEN 'Portfolio growth'
        WHEN 'OPERATIONAL_EXCELLENCE'  THEN 'Operational excellence'
        WHEN 'GOVERNANCE_AND_CONTROLS' THEN 'Governance & controls'
        WHEN 'TALENT_DEVELOPMENT'      THEN 'Talent development'
        ELSE priority
    END;

INSERT INTO app_lm_position_priority (position_id, priority, selected)
SELECT position.id, standard.name, false
FROM app_lm_position position
CROSS JOIN (VALUES ('Capital discipline'),
                   ('Portfolio growth'),
                   ('Operational excellence'),
                   ('Governance & controls'),
                   ('Talent development')) AS standard(name)
WHERE NOT EXISTS (SELECT 1
                  FROM app_lm_position_priority already
                  WHERE already.position_id = position.id
                    AND already.priority = standard.name);

ALTER TABLE app_lm_position_priority ADD COLUMN sort_order integer;

-- The five keep the order the screen listed them in; anything else — there is nothing else today —
-- follows alphabetically, so the slot a row lands in never depends on how the heap hands it over.
UPDATE app_lm_position_priority target
SET sort_order = ordered.slot
FROM (SELECT position_id,
             priority,
             row_number() OVER (
                 PARTITION BY position_id
                 ORDER BY array_position(ARRAY['Capital discipline',
                                               'Portfolio growth',
                                               'Operational excellence',
                                               'Governance & controls',
                                               'Talent development'], priority),
                          priority) - 1 AS slot
      FROM app_lm_position_priority) AS ordered
WHERE target.position_id = ordered.position_id
  AND target.priority = ordered.priority;

ALTER TABLE app_lm_position_priority ALTER COLUMN sort_order SET NOT NULL;

ALTER TABLE app_lm_position_priority
    ADD CONSTRAINT app_lm_position_priority_pkey PRIMARY KEY (position_id, sort_order);

COMMENT ON TABLE app_lm_position_priority IS
    'The strategic priorities offered against this mandate, in the order the brief lists them. selected is whether the mandate is aligned to it; an unselected row is a chip the consultant has not lit.';
