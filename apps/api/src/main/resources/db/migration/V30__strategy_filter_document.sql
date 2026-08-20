-- The Strategy screen stopped being a criteria editor and became a search screen.
--
-- What it was: six sections (sector / company size / geography / ownership / target seeding /
-- off-limits), each with a Required-vs-Preferred mode, each saving through its own PUT into its own
-- child table. A separate Sourcing screen then read that scope and did the browsing. What it is now: one filter panel
-- of flat multi-select chips — industry, location, employees, revenue, off-limits — over the Apollo
-- universe, with the company table beside it. Discovery moved into Strategy; what was Sourcing is now triage.
--
-- Ownership goes with no replacement: app_lm_apollo_companies carries no ownership, org_type or
-- ipo_status column, and nothing derivable from latest_funding (2,123 of 71,822 rows) or
-- parent_company (1,811) is honest at that coverage. Target seeding goes because the new screen has
-- no seeding affordance — a company is either in the filtered result or it is not.
--
-- The whole remaining scope becomes one jsonb document rather than four replacement child tables.
-- This is deliberately against the house pattern of element-collection tables, and the reasoning is
-- the point:
--
--   * The filter is read whole and written whole. The old shape existed because six panels autosaved
--     independently; there is one panel now, and one snapshot PUT.
--   * It is never queried across projects — no join, no GROUP BY, no aggregate. It is loaded by
--     project id and then translated into a WHERE clause against Apollo. A column that is only ever
--     read as a unit does not earn a table.
--   * Saved searches (V32) hold the identical shape. Relational would mean four tables here and four
--     more there, all holding one value object twice. One representation beats two.
--   * The CHECK constraints a relational shape would buy are already bought elsewhere: the payload
--     binds to a typed record, so an unknown band, country or sort token is a 400 from Bean
--     Validation before it ever reaches this column.
--
-- Hibernate maps it with @JdbcTypeCode(SqlTypes.JSON) through Jackson3JsonFormatMapper, which is why
-- the column is jsonb and not text: the driver hands Hibernate a parsed document either way, but
-- jsonb is what lets a future migration reach inside one without reparsing every row in Java.
--
-- '{}' rather than a fully-shaped default: an absent key deserialises to an empty list on the record,
-- so a strategy seeded before its first save reads as "nothing selected" without this file having to
-- restate the payload's shape and drift from it.

ALTER TABLE app_lm_strategy
    ADD COLUMN filter jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT ON COLUMN app_lm_strategy.filter IS
    'The Strategy screen''s whole filter selection, written as one snapshot. Shape owned by StrategyFilter.';

-- Every one of these holds a selection expressed in the brightdata warehouse's vocabulary — sector
-- labels from a 523-value list, ISO country codes, org_type strings, and (source, source_id) company
-- keys. None of it resolves against Apollo, so migrating the rows forward would carry criteria that
-- silently match nothing rather than criteria that match. They are dropped, not translated.
DROP TABLE app_lm_strategy_sector;
DROP TABLE app_lm_strategy_company_size;
DROP TABLE app_lm_strategy_geography;
DROP TABLE app_lm_strategy_ownership;
DROP TABLE app_lm_strategy_target_company;
