-- Package/reporting fields become typed instead of free text, from an owner review of the screen.
--
-- team_size / direct_reports are counts, not prose; notice is a number plus a unit; and the bonus
-- target is a percentage of base. Prior values were free text (only ever seeded null), so the
-- conversions pull any leading digits out and null anything that doesn't parse.

ALTER TABLE app_lm_position
    ALTER COLUMN team_size TYPE integer
        USING NULLIF(regexp_replace(coalesce(team_size, ''), '[^0-9]', '', 'g'), '')::integer,
    ALTER COLUMN direct_reports TYPE integer
        USING NULLIF(regexp_replace(coalesce(direct_reports, ''), '[^0-9]', '', 'g'), '')::integer;

ALTER TABLE app_lm_position DROP COLUMN notice;
ALTER TABLE app_lm_position ADD COLUMN notice_value integer;
ALTER TABLE app_lm_position ADD COLUMN notice_unit varchar(8)
    CONSTRAINT app_lm_position_notice_unit_chk CHECK (notice_unit IN ('WEEKS', 'MONTHS'));

ALTER TABLE app_lm_position DROP COLUMN bonus_target;
ALTER TABLE app_lm_position ADD COLUMN bonus_target_pct integer
    CONSTRAINT app_lm_position_bonus_pct_chk CHECK (bonus_target_pct BETWEEN 0 AND 100);
