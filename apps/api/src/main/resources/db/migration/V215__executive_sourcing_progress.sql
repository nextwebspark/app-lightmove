-- Where each company of a Find executives run has got to while it is searched, so the panel can say what is
-- happening rather than spin: the step, how many people were considered, a step's done/total, and when the company
-- started and finished. Keyed by triage company id. Counts and times only — no person is named here.
-- Written by a single-statement merge that leaves the row's version alone, so the companies searched side by side
-- never contend with the outcome writes.
ALTER TABLE app_lm_executive_sourcing_run
    ADD COLUMN progress jsonb NOT NULL DEFAULT '{}';
