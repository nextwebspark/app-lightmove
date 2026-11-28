-- What an executive expects of a move, beside their current package in the Compensation section: the expected base
-- and total in the record's one currency (never converted), their own words, mobility and when they could start.
-- Only adds, on a table whose row-level security already covers every column.
ALTER TABLE app_lm_person
    ADD COLUMN expected_base_salary   bigint,
    ADD COLUMN expected_total_package bigint,
    ADD COLUMN expected_package_note  text,
    ADD COLUMN mobility               varchar(16),
    ADD COLUMN mobility_note          text,
    ADD COLUMN available_from         text,
    ADD CONSTRAINT app_lm_person_mobility_chk
        CHECK (mobility IN ('NONE', 'COUNTRY', 'REGION', 'INTERNATIONAL')),
    ADD CONSTRAINT app_lm_person_expected_package_note_chk
        CHECK (char_length(expected_package_note) <= 200),
    ADD CONSTRAINT app_lm_person_mobility_note_chk
        CHECK (char_length(mobility_note) <= 200),
    ADD CONSTRAINT app_lm_person_available_from_chk
        CHECK (char_length(available_from) <= 60);
