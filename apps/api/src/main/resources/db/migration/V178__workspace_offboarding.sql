-- Workspace offboarding (#926, part 1): a deleted workspace is purged for good after a grace period, and leaves a
-- deletion certificate; before it goes, an admin can take everything out as one zip.
--
-- The workspace row itself is never deleted: the audit trail and the credit ledger are append-only and the billing
-- tables reference it without a cascade, so a purge ends in a tombstone — name, slug, domain, persona and company
-- snapshot blanked, ids kept. app_lm_workspace carries no policy (EveryWorkspace relies on that), so WorkspacePurge
-- finds the due workspaces with a plain query and then works inside each one's WorkspaceScope.
--
-- purge_notice_email is who the certificate goes to, captured at deletion because nobody can sign in to the workspace
-- by the time the purge runs; it is cleared once the certificate is sent. purge_started_at is when the purge first
-- began (from then on a restore is refused), purge_claimed_until the lease of the instance working on it now, so a
-- purge whose instance died is resumed by the next.

ALTER TABLE app_lm_workspace
    ADD COLUMN deleted_at         timestamptz,
    ADD COLUMN deleted_by         uuid REFERENCES app_lm_user (id) ON DELETE SET NULL,
    ADD COLUMN purge_after        timestamptz,
    ADD COLUMN purge_notice_email varchar(320),
    ADD COLUMN purge_started_at   timestamptz,
    ADD COLUMN purge_claimed_until timestamptz,
    ADD COLUMN purged_at          timestamptz;

-- A workspace deleted before this migration kept every row and file. It gets the grace period from today, so it is
-- purged like any other rather than left behind, and Uncava has the same window to restore one deleted by mistake.
UPDATE app_lm_workspace
SET deleted_at = updated_at, purge_after = now() + interval '30 days'
WHERE status = 'DELETED';

ALTER TABLE app_lm_workspace
    ADD CONSTRAINT app_lm_workspace_deleted_chk
        CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL) AND (deleted_at IS NULL) = (purge_after IS NULL)),
    ADD CONSTRAINT app_lm_workspace_purged_chk
        CHECK (purged_at IS NULL OR (deleted_at IS NOT NULL AND purge_started_at IS NOT NULL));

CREATE INDEX app_lm_workspace_purge_due_idx ON app_lm_workspace (purge_after)
    WHERE purge_after IS NOT NULL AND purged_at IS NULL;

-- Which memberships the deletion ended, so a platform admin's restore brings back exactly those and not a member
-- removed before it. Membership is outside row-level security (TenantTableGuard.EXCLUDED).
ALTER TABLE app_lm_workspace_member
    ADD COLUMN ended_with_workspace boolean NOT NULL DEFAULT false;

-- A deleted workspace's live runs stop.
ALTER TABLE app_lm_outreach_enrollment
    DROP CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk,
    ADD CONSTRAINT app_lm_outreach_enrollment_stop_reason_chk
        CHECK (stop_reason IN ('MANUAL', 'DO_NOT_CONTACT', 'LEFT_THE_RUNNING', 'UNMAPPED', 'ADDRESS_REMOVED',
                               'MAILBOX_INACTIVE', 'MAILBOX_MOVED', 'BOOKING_LINK_UNAVAILABLE', 'SEND_FAILED',
                               'SEND_UNCERTAIN', 'POSITION_ARCHIVED', 'PROCESSING_RESTRICTED',
                               'WORKSPACE_DELETED'));

-- One export of the whole workspace: built off the request thread into the bucket under ws/{id}/exports/, readable
-- until expires_at, then its file is queued for deletion and the row reads EXPIRED.
CREATE TABLE app_lm_workspace_export (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id    uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    requested_by    uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    status          varchar(16)  NOT NULL
        CONSTRAINT app_lm_workspace_export_status_chk CHECK (status IN ('RUNNING', 'READY', 'FAILED', 'EXPIRED')),
    file_name       varchar(200) NOT NULL,
    storage_key     varchar(512),
    size_bytes      bigint,
    people          integer,
    documents       integer,
    positions       integer,
    requested_at    timestamptz  NOT NULL DEFAULT now(),
    completed_at    timestamptz,
    expires_at      timestamptz,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_export_ready_chk
        CHECK (status <> 'READY' OR (storage_key IS NOT NULL AND size_bytes IS NOT NULL AND expires_at IS NOT NULL))
);

-- One export built at a time per workspace, behind the service's own check.
CREATE UNIQUE INDEX app_lm_workspace_export_running_uk ON app_lm_workspace_export (workspace_id)
    WHERE status = 'RUNNING';
CREATE INDEX app_lm_workspace_export_latest_idx ON app_lm_workspace_export (workspace_id, requested_at DESC);
CREATE INDEX app_lm_workspace_export_expiry_idx ON app_lm_workspace_export (expires_at) WHERE status = 'READY';

COMMENT ON TABLE app_lm_workspace_export IS
    'A whole-workspace export: who asked, where its zip is and when it expires. Tenant data.';

CREATE TRIGGER app_lm_workspace_export_touch BEFORE UPDATE ON app_lm_workspace_export
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_workspace_export ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_export FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_export
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The deletion certificate: written when the purge starts (the counts of what it is about to delete, so a purge
-- resumed after a crash still certifies what was there; object_count adds up the files each attempt deleted) and
-- completed when it ends. It names no one — ids, times and
-- counts — and outlives the purge, which never deletes it. body is the exact text body_sha256 is taken over.
CREATE TABLE app_lm_workspace_purge_certificate (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid        NOT NULL REFERENCES app_lm_workspace (id),
    deleted_at       timestamptz NOT NULL,
    deleted_by       uuid,
    purge_started_at timestamptz NOT NULL,
    row_counts       jsonb       NOT NULL,
    retained_counts  jsonb       NOT NULL,
    object_count     bigint      NOT NULL DEFAULT 0,
    purged_at        timestamptz,
    body             text,
    body_sha256      varchar(64),
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT app_lm_workspace_purge_certificate_workspace_uk UNIQUE (workspace_id),
    CONSTRAINT app_lm_workspace_purge_certificate_done_chk
        CHECK ((purged_at IS NULL) = (body IS NULL) AND (body IS NULL) = (body_sha256 IS NULL)),
    CONSTRAINT app_lm_workspace_purge_certificate_hash_chk CHECK (body_sha256 ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE app_lm_workspace_purge_certificate IS
    'What a workspace purge deleted, by table and count. Names no one; kept after the purge. Tenant data.';

CREATE TRIGGER app_lm_workspace_purge_certificate_touch BEFORE UPDATE ON app_lm_workspace_purge_certificate
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

ALTER TABLE app_lm_workspace_purge_certificate ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_workspace_purge_certificate FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_workspace_purge_certificate
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The exports past their expiry, and the ones still building past the deadline a live worker would have met, for
-- WorkspaceExportExpiry: V171's shape, ids and the workspace out, never the row.
CREATE FUNCTION app_lm_resolve_workspace_exports_to_close(p_now timestamptz, p_running_before timestamptz)
    RETURNS TABLE (id uuid, workspace_id uuid)
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT e.id, e.workspace_id FROM app_lm_workspace_export e
        WHERE (e.status = 'READY' AND e.expires_at <= p_now)
           OR (e.status = 'RUNNING' AND e.requested_at < p_running_before);
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

-- Restoring a workspace in its grace period and reading its certificate: Uncava's, never a tenant's.
INSERT INTO app_lm_action (scope, name, description)
VALUES ('PLATFORM', 'WORKSPACE_LIFECYCLE_MANAGE',
        'Restore a deleted workspace within its grace period, and read a purged one''s deletion certificate')
ON CONFLICT (scope, name) DO NOTHING;

INSERT INTO app_lm_role_action (role_id, action_id)
SELECT r.id, a.id
FROM app_lm_role r
JOIN app_lm_action a ON a.scope = 'PLATFORM' AND a.name = 'WORKSPACE_LIFECYCLE_MANAGE'
WHERE r.scope = 'PLATFORM' AND r.name = 'SUPER_ADMIN'
ON CONFLICT (role_id, action_id) DO NOTHING;
