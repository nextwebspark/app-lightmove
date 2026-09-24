-- The position panel's recent activity (GET /projects/{id}/activity) reads one target's successful
-- events newest first, paged by id. None of V1's four indexes covers (target_type, target_id), so
-- without this every page filters and sorts the firm's whole audit history.
--
-- Guarded on ownership. Once harden.sql has run, app_lm_audit_event belongs to postgres and the
-- migrating role cannot index it, so an unguarded CREATE INDEX would fail the deploy. There the
-- index is harden.sql's to create (step 2), and this says so rather than failing.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_audit_event'::regclass))
            = current_user THEN
        CREATE INDEX IF NOT EXISTS app_lm_audit_event_target_idx
            ON app_lm_audit_event (target_type, target_id, id DESC)
            WHERE outcome = 'SUCCESS';
    ELSE
        RAISE WARNING 'app_lm_audit_event is not owned by %, so app_lm_audit_event_target_idx was not created: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
END
$$;
