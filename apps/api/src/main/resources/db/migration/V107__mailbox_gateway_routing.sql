-- A mailbox connection can be held by our own gateway as well as by Nylas (epic #642, story #644).
--
-- gateway says which one made the connection, and every later call for it goes there. A DIRECT connection
-- keeps the provider's refresh token, encrypted (core/crypto, bound to the workspace and the consultant) and
-- never returned or logged; grant_id then holds our own id for it. A NYLAS grant holds no token of ours.
-- recall_calendar_id is the Recall.ai calendar a DIRECT connection's events are read through while its workspace
-- is on RECALL calendar sync; null otherwise, or while the calendar is still owed.

ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN gateway varchar(16) NOT NULL DEFAULT 'NYLAS'
        CONSTRAINT app_lm_mailbox_connection_gateway_chk CHECK (gateway IN ('NYLAS', 'DIRECT')),
    ADD COLUMN refresh_token_encrypted text,
    ADD COLUMN recall_calendar_id varchar(128),
    -- V103's backoff for the Recall calendar a failed create leaves owed: each failure waits longer, and after a
    -- few the mailbox is left until it reconnects, so a row that always fails never starves the newer ones.
    ADD COLUMN recall_calendar_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN recall_calendar_retry_at timestamptz,
    ADD CONSTRAINT app_lm_mailbox_connection_refresh_token_chk
        CHECK ((gateway = 'DIRECT') = (refresh_token_encrypted IS NOT NULL));

-- Recall's webhook names its calendar and nothing else.
CREATE INDEX app_lm_mailbox_connection_recall_calendar_idx ON app_lm_mailbox_connection (recall_calendar_id)
    WHERE recall_calendar_id IS NOT NULL;
