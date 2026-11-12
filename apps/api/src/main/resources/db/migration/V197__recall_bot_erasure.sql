-- Erasing a person (#918): Recall's copies of their calls, queued as their bucket files are (V196).
--
-- An erasure deletes the person's recording rows, and with them the only record of which Recall bots held their voice,
-- so the bots are queued here in the same transaction and RecallBotErasureSweeper calls each back and deletes its
-- media until Recall answers. Global for V196's reason: a bot id is no tenant's data once its rows are gone, and Recall
-- is one platform account, so the table carries no workspace_id and sits outside row-level security.

CREATE TABLE app_lm_recall_bot_erasure (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    recall_bot_id   varchar(64)  NOT NULL,
    call_back       boolean      NOT NULL,
    delete_media    boolean      NOT NULL,
    attempts        integer      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz  NOT NULL DEFAULT now(),
    created_at      timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_recall_bot_erasure_bot_uk UNIQUE (recall_bot_id),
    CONSTRAINT app_lm_recall_bot_erasure_work_ck CHECK (call_back OR delete_media)
);

CREATE INDEX app_lm_recall_bot_erasure_due_idx ON app_lm_recall_bot_erasure (next_attempt_at);

COMMENT ON TABLE app_lm_recall_bot_erasure IS
    'Recall bots of an erased person still to call back and strip of media. Global: holds bot ids, no tenant data.';
