-- Two corrections to the V7 position brief.
--
-- 1. employment_type becomes a closed vocabulary. V7 made it free text, following the mockup's
--    inline input, but it is a small fixed set in practice and structured data filters better
--    downstream. The only value V7 ever seeded was 'Full-time, permanent'; map it, and null any
--    hand-typed value that doesn't fit the catalog rather than guess.
--
-- 2. The mandate keeps ONE target date, on the project. The position's separate start_target was a
--    second, unlinked date — the date entered at project creation never reached the Position screen.
--    It is retired: the Position screen now reads and writes project.target_date.

UPDATE app_lm_position
SET employment_type = CASE employment_type
    WHEN 'Full-time, permanent' THEN 'FULL_TIME_PERMANENT'
    ELSE NULL
END
WHERE employment_type IS NOT NULL;

ALTER TABLE app_lm_position
    ADD CONSTRAINT app_lm_position_employment_type_chk
        CHECK (employment_type IN
            ('FULL_TIME_PERMANENT', 'FIXED_TERM_CONTRACT', 'PART_TIME', 'INTERIM', 'RETAINED_ADVISORY'));

ALTER TABLE app_lm_position DROP COLUMN start_target;
