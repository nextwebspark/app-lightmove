-- Companies captured from the browser, alongside the ones Strategy takes from the Apollo universe.
--
-- The Chrome extension reads the page a consultant is standing on and writes the company into a
-- mandate's triage. Most of what a consultant browses in the GCC is not in app_lm_apollo_companies:
-- the universe holds 71,822 companies and the web holds the rest. Until now this table could not
-- express one — apollo_account_id was NOT NULL and the snapshot was resolved from the universe, so a
-- company Apollo does not publish had no way in.
--
-- So a triage row gains a *second* identity. Apollo's account id stays the preferred one: when a
-- captured page resolves to a universe row, the snapshot is still built server-side from Apollo and
-- the row is indistinguishable from one "Add to Universe" wrote. Only when the universe genuinely has
-- no match does the row fall back to capture_key — the normalised domain — and carry the snapshot the
-- extension read off the page.
--
-- Nothing here touches app_lm_apollo_companies. It is ETL-owned and read-only to the application, and
-- V23's header explains why even a CREATE INDEX on it fails in the deployed database.

ALTER TABLE app_lm_project_triage_company
    -- A captured company has no Apollo id. The CHECK below is what keeps "no identity at all" out.
    ALTER COLUMN apollo_account_id DROP NOT NULL,

    -- How the row arrived, and therefore where its snapshot came from. STRATEGY means the universe
    -- resolved it; CAPTURE means the consultant's browser did, and the fields are as good as the page.
    -- Defaulted so every existing row is correctly labelled without a backfill.
    ADD COLUMN origin varchar(16) NOT NULL DEFAULT 'STRATEGY'
        CONSTRAINT app_lm_project_triage_company_origin_chk
        CHECK (origin IN ('STRATEGY', 'CAPTURE')),

    -- The identity of a company Apollo does not publish: its domain, normalised (lower-cased, no
    -- scheme, no "www.", no path). A domain is the one thing a company page reliably carries and two
    -- pages of the same company agree on, which is what makes it usable as a key at all.
    ADD COLUMN capture_key text,

    ADD COLUMN linkedin_url text,

    -- The page the capture was read from. Provenance for a snapshot nobody else can vouch for.
    ADD COLUMN source_url text,

    -- The consultant's own labels on this company for this mandate. Same scoping as `note`: a tag is
    -- about the mandate's view of the company, not about the company.
    ADD COLUMN tags text[];

ALTER TABLE app_lm_project_triage_company
    ADD CONSTRAINT app_lm_project_triage_company_identity_chk
    CHECK (apollo_account_id IS NOT NULL OR capture_key IS NOT NULL);

COMMENT ON COLUMN app_lm_project_triage_company.capture_key IS
    'Normalised domain — the identity of a triaged company the Apollo universe does not publish. Null when apollo_account_id carries the identity instead.';

-- Both identities need the same one-row-per-company-per-project guarantee, and both have to tolerate
-- the other being null. Partial indexes, because a plain unique index over a nullable column treats
-- every NULL as distinct and would let a project hold the same Apollo company many times over.
--
-- The Apollo index keeps the name it had in V32 so nothing else has to learn a new one.
DROP INDEX app_lm_project_triage_company_uk;

CREATE UNIQUE INDEX app_lm_project_triage_company_uk
    ON app_lm_project_triage_company (project_id, apollo_account_id)
    WHERE apollo_account_id IS NOT NULL;

CREATE UNIQUE INDEX app_lm_project_triage_company_capture_uk
    ON app_lm_project_triage_company (project_id, capture_key)
    WHERE capture_key IS NOT NULL;

-- Note for whoever reads this next: the two identities do not dedupe against each other, and cannot
-- cheaply. A capture only becomes a CAPTURE row when the universe lookup found nothing, so in practice
-- a company is one or the other. A stale lookup — the pipeline publishes a company between the capture
-- and a later Strategy add — could leave a mandate holding both. That is a duplicate row, not a lost
-- decision, and catching it would mean resolving every bulk insert against a domain the Apollo rows
-- spell inconsistently. Left as it is, deliberately.
