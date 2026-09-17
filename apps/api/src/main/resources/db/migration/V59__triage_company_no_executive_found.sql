-- A company a mandate researched and concluded has nobody suitable — distinct from a company nobody
-- has looked at yet, which today reads identically ("+ Add executive") on the Companies grid. Orthogonal
-- to `status`: a company can be IN_UNIVERSE or SHORTLISTED and still carry this flag. Cleared by the
-- application the moment an executive is actually mapped to the company (TriageCompanyService).
-- IF NOT EXISTS because this shipped as V51 first, and merged to main *after* V53-V58 were already
-- applied to the shared databases. Flyway refuses a pending version below the applied maximum, so the
-- file was renumbered to V59; any database that ran it as V51 already holds the column, and drops its
-- stale history row (README, "A migration renumbered after it was applied") rather than the column.
ALTER TABLE app_lm_project_triage_company
    ADD COLUMN IF NOT EXISTS no_executive_found boolean NOT NULL DEFAULT false;
