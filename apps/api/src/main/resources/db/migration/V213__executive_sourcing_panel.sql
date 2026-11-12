-- Find executives becomes a panel the consultant steers (docs/find-executives.md): the search is saved per
-- position and edited in place, a run stores what it found at each company instead of filing anyone, and the
-- consultant adds the people they tick.

-- The position's search: one row, the words, countries and profiles-per-company as last drafted or edited.
-- `brief_fingerprint` is the brief the AI drafted from, so the panel can tell when the brief has moved on.
CREATE TABLE app_lm_executive_sourcing_criteria (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE
        CONSTRAINT app_lm_executive_sourcing_criteria_project_uk UNIQUE,
    criteria          jsonb       NOT NULL,
    source            varchar(16) NOT NULL
        CONSTRAINT app_lm_executive_sourcing_criteria_source_chk CHECK (source IN ('DEFAULT', 'AI', 'EDITED')),
    brief_fingerprint varchar(64),
    updated_by        uuid        REFERENCES app_lm_user (id) ON DELETE SET NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    version           bigint      NOT NULL DEFAULT 0
);

COMMENT ON TABLE app_lm_executive_sourcing_criteria IS
    'One Find executives search per position. Tenant data, staff-only: every read filters by project_id within '
    'the caller''s workspace.';

CREATE TRIGGER app_lm_executive_sourcing_criteria_touch BEFORE UPDATE ON app_lm_executive_sourcing_criteria
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();

-- What a run found at one company, one row a person: the profile slug and our own judgement of them, never a
-- name or a title — those are read from the people cache, so a row outlives nothing the cache has let go.
-- `reasons` are our codes and our search's own words. Whether someone was added is read from the position's
-- mapped profiles, so there is no status. Rows older than the people cache's life are deleted by the next run.
CREATE TABLE app_lm_executive_sourcing_suggestion (
    id                uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id      uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id        uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    run_id            uuid        NOT NULL REFERENCES app_lm_executive_sourcing_run (id) ON DELETE CASCADE,
    triage_company_id uuid        NOT NULL REFERENCES app_lm_project_triage_company (id) ON DELETE CASCADE,
    linkedin_slug     varchar(200) NOT NULL,
    verdict           varchar(16) NOT NULL
        CONSTRAINT app_lm_executive_sourcing_suggestion_verdict_chk CHECK (verdict IN ('FITS', 'NO_TITLE', 'NOT_FIT')),
    score             smallint,
    rank              smallint    NOT NULL,
    reasons           text[]      NOT NULL DEFAULT '{}',
    found_by          varchar(16) NOT NULL
        CONSTRAINT app_lm_executive_sourcing_suggestion_found_by_chk CHECK (found_by IN ('LIVE', 'INDEX', 'BOTH')),
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT app_lm_executive_sourcing_suggestion_uk UNIQUE (run_id, triage_company_id, linkedin_slug)
);

COMMENT ON TABLE app_lm_executive_sourcing_suggestion IS
    'The people a Find executives run found at each company, judged and ranked; staff-only tenant data.';

CREATE INDEX app_lm_executive_sourcing_suggestion_company_idx
    ON app_lm_executive_sourcing_suggestion (project_id, triage_company_id, created_at DESC);
CREATE INDEX app_lm_executive_sourcing_suggestion_created_idx
    ON app_lm_executive_sourcing_suggestion (created_at);

-- A run keeps the search as it was pressed, how many people it suggested, and a stop asked for while it ran:
-- the companies already in flight finish and the rest read STOPPED.
ALTER TABLE app_lm_executive_sourcing_run
    ADD COLUMN criteria          jsonb,
    ADD COLUMN suggested         integer NOT NULL DEFAULT 0,
    ADD COLUMN stop_requested_at timestamptz;

ALTER TABLE app_lm_executive_sourcing_run
    DROP CONSTRAINT app_lm_executive_sourcing_run_status_chk;

ALTER TABLE app_lm_executive_sourcing_run
    ADD CONSTRAINT app_lm_executive_sourcing_run_status_chk
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'STOPPED'));

-- A run that filed on its own kept whom it filed, by name, in each outcome's `picks`; nothing reads them now, so
-- they go rather than outlive an erasure. The counts beside them stay.
UPDATE app_lm_executive_sourcing_run AS run
SET outcomes = (SELECT jsonb_agg(outcome - 'picks' ORDER BY position)
                FROM jsonb_array_elements(run.outcomes) WITH ORDINALITY AS listed(outcome, position))
WHERE jsonb_path_exists(run.outcomes, '$[*].picks');

ALTER TABLE app_lm_executive_sourcing_criteria ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_executive_sourcing_criteria FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_executive_sourcing_criteria
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));

ALTER TABLE app_lm_executive_sourcing_suggestion ENABLE ROW LEVEL SECURITY;
ALTER TABLE app_lm_executive_sourcing_suggestion FORCE ROW LEVEL SECURITY;
CREATE POLICY app_lm_tenant ON app_lm_executive_sourcing_suggestion
    USING (app_lm_tenant_visible(workspace_id))
    WITH CHECK (app_lm_tenant_visible(workspace_id));
