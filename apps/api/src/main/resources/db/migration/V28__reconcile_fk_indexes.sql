-- Makes V26's end state the same everywhere, whatever V26 actually did in each database.
--
-- V26 was applied to the shared Cloud SQL database by a developer booting `npm run dev:cloud` against
-- it while holding an uncommitted work-in-progress version of the file, so the schema history there
-- records a checksum (597398679) matching no version of V26 that has ever existed in this repository
-- — not the four-index original (-1116529955), not the three-index file main carries today
-- (251586809), and not a line-ending variant of either. That mismatch failed Flyway validation and
-- blocked every deploy after it, and the content that produced it is unrecoverable, so the only way
-- out is `flyway repair`. Repair rewrites the checksum and nothing else: it marks version 26 applied,
-- so V26 will never run there, and whichever indexes that intermediate version happened not to create
-- would never appear.
--
-- Hence this. It states the intended end state outright rather than depending on what ran before:
-- the three covering indexes issue #21 asked for, and not the one PR #64 deliberately dropped
-- ("app_lm_position.locked_by is intentionally left unindexed"). IF NOT EXISTS / IF EXISTS because a
-- database rebuilt from V1 arrives here having just created all of them in V26, while the repaired
-- shared database arrives in an unknown state. Both leave identical.

CREATE INDEX IF NOT EXISTS app_lm_invitation_invited_by_idx   ON app_lm_invitation (invited_by);
CREATE INDEX IF NOT EXISTS app_lm_invitation_accepted_by_idx  ON app_lm_invitation (accepted_by_user_id);
CREATE INDEX IF NOT EXISTS app_lm_project_member_added_by_idx ON app_lm_project_member (added_by);

DROP INDEX IF EXISTS app_lm_position_locked_by_idx;
