-- Records app_lm_refresh_token_live_idx, which exists on the shared database but in no migration.
--
-- It was created there on 2026-08-15 by a migration called V26__user_revoked_sessions.sql that has
-- never existed in this repository — a developer's uncommitted file, applied by booting
-- `npm run dev:cloud` against the shared database (CLAUDE.md: that command "applies your migrations to
-- everyone at boot"). Two days later V26__fk_indexes.sql claimed the same version number here, and the
-- collision failed Flyway validation on every deploy until the history was repaired.
--
-- Repair realigns the checksum and nothing else, so the record of what that file did would have been
-- lost while its index quietly stayed in the schema — present in production, absent from every
-- environment built from this repository. Comparing the live schema against a fresh build identified
-- it exactly, and this is that index, written down.
--
-- IF NOT EXISTS because the shared database already has it and a fresh build does not.

CREATE INDEX IF NOT EXISTS app_lm_refresh_token_live_idx
    ON app_lm_refresh_token (user_id, created_at DESC)
    WHERE revoked_at IS NULL;
