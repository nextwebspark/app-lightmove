-- One Find executives run in progress per mandate. The service refuses a second request, but two
-- racing past that check would both spend; this index makes the database refuse the later insert.
CREATE UNIQUE INDEX app_lm_executive_sourcing_run_one_in_progress_uk
    ON app_lm_executive_sourcing_run (project_id)
    WHERE status IN ('QUEUED', 'RUNNING');
