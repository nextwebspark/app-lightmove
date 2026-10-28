-- #1049: a position description's package is read again, so the compensation scalars join V67's
-- `field_sources` map — a reading fills a TEMPLATE or unclaimed figure and never a MANUAL one.
--
-- V67's backfill rule, for the same reason: a brief nobody has saved since seeding (version = 0) is
-- the template's, so the scalars PositionTemplateApplier writes (currency, base_salary_mode,
-- bonus_value, bonus_basis, incentive_type, incentive_vesting — never the salary band or the
-- incentive amount, which a template does not carry) become TEMPLATE. A saved brief is somebody's:
-- every non-null scalar becomes MANUAL, since a legacy row cannot be attributed field by field.
-- Existing keys of the other steps are kept as they are.

UPDATE app_lm_position
SET field_sources = field_sources || (
    CASE WHEN version = 0 THEN
        (CASE WHEN currency IS NOT NULL THEN jsonb_build_object('currency', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN base_salary_mode IS NOT NULL THEN jsonb_build_object('baseSalaryMode', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN bonus_value IS NOT NULL THEN jsonb_build_object('bonusValue', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN bonus_basis IS NOT NULL THEN jsonb_build_object('bonusBasis', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN incentive_type IS NOT NULL THEN jsonb_build_object('incentiveType', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN incentive_vesting IS NOT NULL THEN jsonb_build_object('incentiveVesting', 'TEMPLATE') ELSE '{}' END)
    ELSE
        (CASE WHEN currency IS NOT NULL THEN jsonb_build_object('currency', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN salary_min IS NOT NULL THEN jsonb_build_object('salaryMin', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN salary_max IS NOT NULL THEN jsonb_build_object('salaryMax', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN base_salary_mode IS NOT NULL THEN jsonb_build_object('baseSalaryMode', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN bonus_value IS NOT NULL THEN jsonb_build_object('bonusValue', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN bonus_basis IS NOT NULL THEN jsonb_build_object('bonusBasis', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN incentive_type IS NOT NULL THEN jsonb_build_object('incentiveType', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN incentive_amount IS NOT NULL THEN jsonb_build_object('incentiveAmount', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN incentive_vesting IS NOT NULL THEN jsonb_build_object('incentiveVesting', 'MANUAL') ELSE '{}' END)
    END
);

COMMENT ON COLUMN app_lm_position.field_sources IS
    'Provenance of the scalars a template or a document reading can claim (details, context, reporting and, since V160, compensation), keyed by wire field name. An absent key means nobody has claimed the field yet.';
