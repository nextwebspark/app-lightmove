-- A company a mandate researched and concluded has nobody suitable — distinct from a company nobody
-- has looked at yet, which today reads identically ("+ Add executive") on the Companies grid. Orthogonal
-- to `status`: a company can be IN_UNIVERSE or SHORTLISTED and still carry this flag. Cleared by the
-- application the moment an executive is actually mapped to the company (TriageCompanyService).
ALTER TABLE app_lm_project_triage_company
    ADD COLUMN no_executive_found boolean NOT NULL DEFAULT false;
