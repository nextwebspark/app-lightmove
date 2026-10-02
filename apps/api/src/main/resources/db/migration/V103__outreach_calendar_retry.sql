-- A calendar the mail service cannot read (a grant connected before the calendar scope was asked for)
-- would otherwise be asked again on every reply poll, on every instance, for ever. Each failed read
-- pushes the next one further out, and after a handful the calendar is left until the mailbox is
-- reconnected, which clears both.
ALTER TABLE app_lm_mailbox_connection
    ADD COLUMN calendar_sync_attempts  integer      NOT NULL DEFAULT 0,
    ADD COLUMN calendar_sync_retry_at  timestamptz;
