-- The customer audit log (GET /workspace/audit-events) reads one workspace's events newest first, paged
-- by id. V1's (workspace_id, occurred_at) index orders by time, not by the id the cursor walks, so a
-- page would sort the firm's whole trail. An index only: no row is written or changed.
--
-- Guarded on ownership for V74's reason: once harden.sql has run the table belongs to postgres, and
-- there the index is harden.sql's to create (step 2), built concurrently so audit inserts keep landing.
DO $$
BEGIN
    IF pg_get_userbyid((SELECT relowner FROM pg_class WHERE oid = 'app_lm_audit_event'::regclass))
            = current_user THEN
        CREATE INDEX IF NOT EXISTS app_lm_audit_event_workspace_id_idx
            ON app_lm_audit_event (workspace_id, id DESC);
    ELSE
        RAISE WARNING 'app_lm_audit_event is not owned by %, so app_lm_audit_event_workspace_id_idx was not created: re-run ops/cloudsql/harden.sql as postgres.', current_user;
    END IF;
END
$$;
