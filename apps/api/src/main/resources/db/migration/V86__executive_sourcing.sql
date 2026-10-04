-- Find executives: the AI-driven sourcing run that files people into a mandate from the In-universe
-- page, and the fifth door a candidate can arrive through.
--
-- A sourced executive is a row like any other; only its source differs (V47's idiom). The contact
-- ledger's CHECK gains the same value because ContactSource.ofDoor is an exhaustive switch over the
-- row doors — a search hit carries no contacts, so nothing ever writes it.

ALTER TABLE app_lm_project_candidate
    DROP CONSTRAINT app_lm_project_candidate_source_chk;

ALTER TABLE app_lm_project_candidate
    ADD CONSTRAINT app_lm_project_candidate_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'AI_SOURCED'));

COMMENT ON COLUMN app_lm_project_candidate.source IS
    'Which door the profile came through: MANUAL (typed in), CSV (spreadsheet import), EXTENSION (browser plugin), AI_SOURCED (Find executives).';

ALTER TABLE app_lm_candidate_contact
    DROP CONSTRAINT app_lm_candidate_contact_source_chk;

ALTER TABLE app_lm_candidate_contact
    ADD CONSTRAINT app_lm_candidate_contact_source_chk
        CHECK (source IN ('MANUAL', 'CSV', 'EXTENSION', 'CONTACTOUT', 'AI_SOURCED'));

-- One run of Find executives. A row rather than the audit trail or the project stream: the stream's
-- frames carry no content and die with the tab, the audit ledger is append-only and allow-listed
-- for the activity feed, and the screen needs a status to poll, a summary to read back after a
-- reload, and a guard against a second run starting while one is still spending.
--
-- `companies` is frozen at request time so the worker never re-reads a stage that may have moved
-- under it; `outcomes` grows one entry per company as each finishes.
CREATE TABLE app_lm_executive_sourcing_run (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id     uuid        NOT NULL REFERENCES app_lm_workspace (id) ON DELETE CASCADE,
    project_id       uuid        NOT NULL REFERENCES app_lm_project (id) ON DELETE CASCADE,
    requested_by     uuid        NOT NULL REFERENCES app_lm_user (id) ON DELETE CASCADE,

    status           varchar(16) NOT NULL
        CONSTRAINT app_lm_executive_sourcing_run_status_chk
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),

    companies        jsonb       NOT NULL,
    spec             jsonb,
    outcomes         jsonb       NOT NULL DEFAULT '[]'::jsonb,

    companies_done   integer     NOT NULL DEFAULT 0,
    executives_filed integer     NOT NULL DEFAULT 0,
    -- Every hit the vendor returned is billed, so the run keeps its own count of them.
    vendor_hits      integer     NOT NULL DEFAULT 0,
    model_calls      integer     NOT NULL DEFAULT 0,

    error            text,
    started_at       timestamptz,
    finished_at      timestamptz,

    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0
);

-- The screen's two reads: the latest run of a mandate, and whether one is still in progress.
CREATE INDEX app_lm_executive_sourcing_run_project_idx
    ON app_lm_executive_sourcing_run (project_id, created_at DESC);

CREATE TRIGGER app_lm_executive_sourcing_run_touch BEFORE UPDATE ON app_lm_executive_sourcing_run
    FOR EACH ROW EXECUTE FUNCTION app_lm_touch_updated_at();
