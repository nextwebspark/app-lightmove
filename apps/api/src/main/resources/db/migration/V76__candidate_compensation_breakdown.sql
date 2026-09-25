-- The executive drawer's Compensation editor itemises what V36 stored as two bare figures: the
-- allowances as named lines (housing, transport, education, …) and the long-term incentive as the
-- instruments it is paid in (options, RSUs, cash). One document read and written whole with the
-- section and never queried — V30's argument, as for `profile`.
--
-- `allowances` stays the stored total and the one every reader sums, so the report, the export and
-- the importer read exactly what they read before; `CandidateCompensation` keeps the two agreeing.
ALTER TABLE app_lm_project_candidate
    ADD COLUMN compensation_breakdown jsonb NOT NULL DEFAULT '{}'::jsonb;
