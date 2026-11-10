-- Account deletion (#926 part 2). A deleted account keeps its row, so the notes, tasks and audit lines that name its
-- id still resolve to someone — "Former member" — while everything that identified the person is scrubbed from it.
-- deleted_at is when that happened. It implies DELETED rather than the reverse: a row set DELETED by hand before this
-- column existed has no date to give it.

ALTER TABLE app_lm_user ADD COLUMN deleted_at timestamptz;

ALTER TABLE app_lm_user
    ADD CONSTRAINT app_lm_user_deleted_at_chk CHECK (deleted_at IS NULL OR status = 'DELETED');
