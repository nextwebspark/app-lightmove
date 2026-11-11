-- Takes people's names and addresses out of audit rows written before #927 made audit details ids only.
--
-- Run ONCE, AFTER the release that records ids only is deployed — run before it, the old revision keeps writing names
-- into new rows in the meantime — as the audit table's owner (postgres once harden.sql has run), in a quiet moment:
--
--   gcloud sql connect bright-gcc --user=postgres --database=lightmove < ops/cloudsql/scrub-audit-pii.sql
--   docker exec -i lm-dev-pg psql -U postgres -d lightmove < ops/cloudsql/scrub-audit-pii.sql   # npm run dev
--
-- The trail is append-only by trigger and readable only through row-level security that has no UPDATE policy, so
-- an UPDATE would refuse or silently touch nothing. Inside this one transaction the trigger is disabled and FORCE is
-- lifted, so the owner reaches every row; both are back before the commit, and a failure rolls all of it back.
-- ALTER TABLE holds an exclusive lock for the transaction: audit writes wait for it (they are asynchronous).
--
-- What goes, and only that — no event changes what it says happened:
--   * `email` on every row; on a sign-in refused for an unknown address it becomes the address's `domain` — read after
--     the last @, and none where that is empty — as AuthenticationService now records it;
--   * `fullName` on every row (CANDIDATE_REMOVED, PROFILE_UPDATED);
--   * `fileName` on a person's document (PERSON_DOCUMENT_ADDED / _VERSION_ADDED), a CV's name being its person's.
--     A position description's and a spreadsheet's keep theirs.
-- Running it twice changes nothing the second time.

\set ON_ERROR_STOP on

BEGIN;

DO $$
DECLARE
    owner_role text := pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_audit_event'::regclass));
BEGIN
    IF owner_role <> current_user
            AND NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user) THEN
        RAISE EXCEPTION 'Run this as %, the owner of app_lm_audit_event, not as %', owner_role, current_user;
    END IF;
END $$;

-- FORCE is put back only where it was: a deployment whose harden.sql has not yet re-run step 2c stays as it was.
CREATE TEMP TABLE audit_rls_before ON COMMIT DROP AS
    SELECT relforcerowsecurity AS was_forced FROM pg_class WHERE oid = 'app_lm_audit_event'::regclass;
CREATE TEMP TABLE scrubbed (step text, rows bigint) ON COMMIT DROP;

ALTER TABLE app_lm_audit_event NO FORCE ROW LEVEL SECURITY;
ALTER TABLE app_lm_audit_event DISABLE TRIGGER app_lm_audit_event_append_only;

WITH changed AS (
    UPDATE app_lm_audit_event
       SET metadata = (metadata - 'email')
               || jsonb_build_object('domain', lower(substring(metadata ->> 'email' from '[^@]*$')))
     WHERE event_type = 'LOGIN_FAILED' AND metadata ->> 'reason' = 'no_such_user'
       AND position('@' in metadata ->> 'email') > 1
       AND substring(metadata ->> 'email' from '[^@]*$') <> ''
    RETURNING 1)
INSERT INTO scrubbed SELECT 'email → domain on an unknown sign-in', count(*) FROM changed;

WITH changed AS (
    UPDATE app_lm_audit_event SET metadata = metadata - 'email' WHERE metadata ? 'email' RETURNING 1)
INSERT INTO scrubbed SELECT 'email', count(*) FROM changed;

WITH changed AS (
    UPDATE app_lm_audit_event SET metadata = metadata - 'fullName' WHERE metadata ? 'fullName' RETURNING 1)
INSERT INTO scrubbed SELECT 'fullName', count(*) FROM changed;

WITH changed AS (
    UPDATE app_lm_audit_event SET metadata = metadata - 'fileName'
     WHERE event_type IN ('PERSON_DOCUMENT_ADDED', 'PERSON_DOCUMENT_VERSION_ADDED') AND metadata ? 'fileName'
    RETURNING 1)
INSERT INTO scrubbed SELECT 'fileName on a person''s document', count(*) FROM changed;

ALTER TABLE app_lm_audit_event ENABLE TRIGGER app_lm_audit_event_append_only;
DO $$
BEGIN
    IF (SELECT was_forced FROM audit_rls_before) THEN
        ALTER TABLE app_lm_audit_event FORCE ROW LEVEL SECURITY;
    END IF;
END $$;

SELECT step, rows FROM scrubbed;

COMMIT;
