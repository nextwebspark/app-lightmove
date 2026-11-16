-- A spreadsheet import's LinkedIn enrichment (docs/spreadsheet-import.md): one row per person or company the import
-- asked to have researched, drained a few at a time by ImportEnrichmentWorker, so a 250-link file never puts a
-- vendor call on the import's own request thread and survives the instance that took it. `run_id` groups one
-- import's rows for the dialog's progress line; there is no run table. The row holds a profile or company slug and
-- our own outcome, never a name — finished rows go after lightmove.spreadsheet-import.enrichment-keep-for, by a daily
-- purge of their own.
CREATE TABLE app_lm_import_enrichment_item (
    id            uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id  uuid         NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id    uuid         NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    run_id        uuid         NOT NULL,
    requested_by  uuid         REFERENCES app_lm_user (id) ON DELETE SET NULL,
    kind          varchar(16)  NOT NULL
        CONSTRAINT app_lm_import_enrichment_item_kind_chk CHECK (kind IN ('PERSON', 'COMPANY')),
    -- A candidate's id for PERSON, a triage company's for COMPANY: two tables, so no foreign key; a target removed
    -- before its turn is answered as removed by the service that would have written it.
    target_id     uuid         NOT NULL,
    linkedin_slug varchar(200) NOT NULL,
    status        varchar(24)  NOT NULL DEFAULT 'QUEUED'
        CONSTRAINT app_lm_import_enrichment_item_status_chk CHECK (status IN ('QUEUED', 'RUNNING', 'RESEARCHED',
            'ALREADY_RESEARCHED', 'NOT_FOUND', 'REFUSED', 'OVER_ALLOWANCE', 'UNAVAILABLE', 'FAILED')),
    attempts      smallint     NOT NULL DEFAULT 0,
    claimed_at    timestamptz,
    finished_at   timestamptz,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_import_enrichment_item_uk UNIQUE (run_id, kind, target_id)
);

COMMENT ON TABLE app_lm_import_enrichment_item IS
    'The people and companies a spreadsheet import asked to have researched from LinkedIn, and what came of each. '
    'Staff-only tenant data.';

CREATE INDEX app_lm_import_enrichment_item_due_idx
    ON app_lm_import_enrichment_item (workspace_id, created_at) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX app_lm_import_enrichment_item_run_idx ON app_lm_import_enrichment_item (run_id);
CREATE INDEX app_lm_import_enrichment_item_finished_idx
    ON app_lm_import_enrichment_item (finished_at) WHERE finished_at IS NOT NULL;

ALTER TABLE app_lm_import_enrichment_item ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_import_enrichment_item FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_import_enrichment_item
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

-- The worker's walk (V191's idiom): a row waiting its turn, or one claimed by an instance that died before finishing
-- it. Wider than the claim, never narrower.
CREATE FUNCTION app_lm_workspaces_with_import_enrichment_due(p_stale_before timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT i.workspace_id FROM app_lm_import_enrichment_item i
         WHERE i.status = 'QUEUED' OR (i.status = 'RUNNING' AND i.claimed_at < p_stale_before);
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION app_lm_workspaces_with_import_enrichment_due(timestamptz) FROM PUBLIC;

-- The daily retention purge: finished rows past their keep-for, in a workspace that may never import again.
CREATE FUNCTION app_lm_workspaces_with_import_enrichment_expired(p_finished_before timestamptz)
    RETURNS SETOF uuid
    LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    outside text := current_setting('app.rls_maintenance', true);
BEGIN
    PERFORM set_config('app.rls_maintenance', 'on', true);
    RETURN QUERY
        SELECT DISTINCT i.workspace_id FROM app_lm_import_enrichment_item i
         WHERE i.finished_at < p_finished_before;
    PERFORM set_config('app.rls_maintenance', coalesce(outside, ''), true);
END
$$;

REVOKE ALL ON FUNCTION app_lm_workspaces_with_import_enrichment_expired(timestamptz) FROM PUBLIC;

-- An import's paid LinkedIn read is metered for fair use as PROFILE_ENRICH. Without it in the list every record was
-- refused by this check, which UsageRecorder logs and swallows, so enrichment was never counted against the ceiling.
ALTER TABLE app_lm_usage_event DROP CONSTRAINT app_lm_usage_event_kind_chk;
ALTER TABLE app_lm_usage_event ADD CONSTRAINT app_lm_usage_event_kind_chk
    CHECK (kind IN ('PEOPLE_SEARCH_PAGE', 'SOURCING_RUN', 'AI_ENRICH', 'PROFILE_ENRICH', 'OUTREACH_OPENER',
                    'ASSISTANT_ASK'));
