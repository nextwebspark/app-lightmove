-- Drop the employment_type CHECK added in V8. The value set is owned by the EmploymentType Java enum
-- (@Enumerated(STRING)); the database CHECK was redundant with it. Dropped here rather than edited
-- into V8, which is already applied — editing an applied migration breaks Flyway's checksum.

ALTER TABLE app_lm_position DROP CONSTRAINT app_lm_position_employment_type_chk;
