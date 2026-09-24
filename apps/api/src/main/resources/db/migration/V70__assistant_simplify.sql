-- The assistant answers in one request now: no background turn, no event log, no stream. A turn row is
-- written only once the model has answered, and the company card it proposed lives on the row itself.

DROP TABLE app_lm_assistant_event;
DROP FUNCTION app_lm_assistant_event_is_immutable();

DELETE FROM app_lm_assistant_turn WHERE status <> 'SUCCEEDED' OR answer IS NULL;

DROP INDEX app_lm_assistant_turn_running_idx;

ALTER TABLE app_lm_assistant_turn
    DROP CONSTRAINT app_lm_assistant_turn_finished_chk,
    DROP CONSTRAINT app_lm_assistant_turn_succeeded_chk,
    DROP COLUMN status,
    DROP COLUMN error_code,
    DROP COLUMN ip_address,
    DROP COLUMN user_agent,
    DROP COLUMN correlation_id,
    DROP COLUMN model,
    DROP COLUMN input_tokens,
    DROP COLUMN output_tokens,
    DROP COLUMN started_at,
    DROP COLUMN finished_at,
    ALTER COLUMN answer SET NOT NULL,
    ADD COLUMN proposal jsonb,
    ADD COLUMN proposal_accepted jsonb;

CREATE INDEX app_lm_assistant_thread_project_idx
    ON app_lm_assistant_thread (workspace_id, user_id, project_id, updated_at DESC);

COMMENT ON TABLE app_lm_assistant_turn IS
    'One answered question. proposal is the company card the answer carried; proposal_accepted is what the user filed from it.';
