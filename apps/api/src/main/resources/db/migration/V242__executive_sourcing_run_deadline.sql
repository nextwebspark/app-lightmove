-- A Find executives run keeps the deadline it was requested under (#1314): run-deadline became an application
-- variable a super admin changes at runtime, and a run in flight is judged lost against its own deadline, never
-- against the current one. Null on runs from before this migration, which are judged against the current value.

ALTER TABLE app_lm_executive_sourcing_run
    ADD COLUMN run_deadline_seconds integer;

COMMENT ON COLUMN app_lm_executive_sourcing_run.run_deadline_seconds IS
    'The run-deadline in force when the run was requested. Null on runs older than V242.';
