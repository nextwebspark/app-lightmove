-- Per-version monitoring (#1323): what the daily call counts lacked, and how often a person kept an AI answer.
-- Counts only, as before: no prompt, no answer, no workspace.

ALTER TABLE app_lm_platform_prompt_call_daily
    -- The injection guard answered in the model's place: no call was made, so none is counted beside it.
    ADD COLUMN guard_refusals  bigint NOT NULL DEFAULT 0,
    -- An answer failed its schema and the model was asked again (each re-ask is also a call).
    ADD COLUMN schema_reasks   bigint NOT NULL DEFAULT 0,
    -- The model answered and the answer still could not be read into what the feature expects.
    ADD COLUMN schema_failures bigint NOT NULL DEFAULT 0;

-- What a person did with an answer a screen asked them to keep or change, per version and UTC day of the decision.
CREATE TABLE app_lm_platform_prompt_outcome_daily (
    prompt_id varchar(80) NOT NULL,
    version   integer     NOT NULL,
    day       date        NOT NULL,
    accepted  bigint      NOT NULL DEFAULT 0,
    changed   bigint      NOT NULL DEFAULT 0,
    PRIMARY KEY (prompt_id, version, day)
);

COMMENT ON TABLE app_lm_platform_prompt_outcome_daily IS
    'AI answers a person kept or changed, per prompt version and UTC day. Counts only, never content or who.';

-- Which prompt version proposed each value still flagged in ai_inferred_fields, so the decision on it can be counted
-- against that version. Null and missing keys: proposed before this column, counted nowhere.
ALTER TABLE app_lm_person ADD COLUMN ai_inferred_by jsonb;
