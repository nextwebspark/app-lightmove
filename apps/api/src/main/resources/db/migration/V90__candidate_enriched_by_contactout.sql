-- ContactOut becomes a third provider behind a researched profile: Find executives can search its
-- people index instead of Bright Data's (lightmove.enrichment.sourcing.people-source), and the hit it
-- files is the research. V53's CHECK names the vendors a row may record, so it widens by one.
ALTER TABLE app_lm_project_candidate
    DROP CONSTRAINT app_lm_project_candidate_enriched_by_chk;

ALTER TABLE app_lm_project_candidate
    ADD CONSTRAINT app_lm_project_candidate_enriched_by_chk
        CHECK (enriched_by IN ('BRIGHTDATA', 'HARVESTAPI', 'CONTACTOUT'));
