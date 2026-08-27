-- The Position screen becomes the six-step wizard the mockup draws, and the brief grows into the
-- field set those steps collect: a department and the role's key responsibilities, why the mandate
-- exists and how urgently, the org chart around the seat, a package quoted the way GCC packages
-- actually are, a description beside every competency, and a record that somebody called the brief
-- ready.
--
-- Publishing is a stamp and nothing else. V38 retired the position lock deliberately, so there is no
-- readiness gate here, nothing freezes, and a published brief stays editable. published_at says
-- somebody declared it ready; updated_at says it changed. Both stay true at once.
--
-- Four columns change shape rather than being dropped and re-added empty:
--
--   * direct_reports was a count (V9) and becomes the {title, name} list the org chart draws. A count
--     of four converts to four rows with neither field filled, because the number somebody entered is
--     a fact and dropping it to avoid four blank cards would throw it away. A row with both fields
--     blank is discarded the next time the step is saved, so the placeholders clear themselves.
--   * team_size was a count (V9) and goes back to free text. V9 assumed people write "38"; what they
--     actually write is "38 across the finance function", and the regexp that keeps the 38 discards
--     the half of the answer carrying the meaning.
--   * bonus_target_pct becomes a value plus the basis it is quoted in — percent of base, percent of
--     total fixed, or months of base. Existing values are percentages of base by definition, so the
--     conversion is exact. numeric, not integer: 12.5% of base and 1.5 months are both real answers.
--   * ltip was one free-text line and becomes an instrument, an amount and a vesting schedule. The
--     old line was prose about vesting ("3-year vesting, 33% annually"), so it lands in the schedule;
--     the instrument and the amount stay null rather than being guessed out of prose.
--
-- V20's seniority and reports_to_name are untouched here. They have sat in the table since V20 mapped
-- by nothing at all; the wizard needs both, so they finally get entity fields rather than a DROP.
--
-- Enums stay Java-owned: no CHECK on any vocabulary column added below, following V10, V20 and V21.
-- The one CHECK dropped is V9's on notice_unit, which the wizard's third option (Days) breaks — the
-- same reason V21 dropped mandate_reason's to make room for GROWTH_EXPANSION, which now exists.
--
-- Every owned list stays a child table, as V7's three already are: one composite-PK table per list,
-- no identity beyond the slot, replaced wholesale by its section's write. The jsonb precedent (V30's
-- filter, V36's profile) is for a heterogeneous document read back whole by one screen; these are
-- flat uniform rows, and mixing both idioms inside one aggregate would leave the next reader guessing
-- which a new list should be.

-- ── Step 1 · Position details ────────────────────────────────────────────────

ALTER TABLE app_lm_position ADD COLUMN department varchar(160);

CREATE TABLE app_lm_position_responsibility (
    position_id uuid         NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    text        varchar(200) NOT NULL,
    PRIMARY KEY (position_id, sort_order)
);

COMMENT ON TABLE app_lm_position_responsibility IS
    'The role''s key responsibilities, in the order the brief lists them.';

-- ── Step 2 · Mandate context ─────────────────────────────────────────────────

ALTER TABLE app_lm_position ADD COLUMN business_driver text;

ALTER TABLE app_lm_position
    ADD COLUMN hiring_urgency varchar(16) NOT NULL DEFAULT 'STANDARD';

COMMENT ON COLUMN app_lm_position.hiring_urgency IS
    'STANDARD / PRIORITY / URGENT — the 90, 60 and 30 day framings the screen labels them with.';

-- A set, not a sequence: the screen offers a fixed row of priorities and each is on or off, so the
-- primary key is the pair and there is no sort_order to keep. Not booleans on app_lm_position either
-- — a sixth priority would then be a migration rather than an enum constant.
CREATE TABLE app_lm_position_priority (
    position_id uuid        NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    priority    varchar(32) NOT NULL,
    PRIMARY KEY (position_id, priority)
);

COMMENT ON TABLE app_lm_position_priority IS
    'The strategic priorities this mandate is said to serve. One row per selected priority.';

-- ── Step 3 · Reporting ───────────────────────────────────────────────────────

CREATE TABLE app_lm_position_direct_report (
    position_id uuid         NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order  integer      NOT NULL,
    title       varchar(160),
    name        varchar(160),
    PRIMARY KEY (position_id, sort_order)
);

COMMENT ON TABLE app_lm_position_direct_report IS
    'The seats reporting into this one. Either field may be blank: a mandate often knows the seat long before the person.';

-- The count becomes that many unfilled seats, so nothing anyone typed is lost.
INSERT INTO app_lm_position_direct_report (position_id, sort_order)
SELECT p.id, seat.ordinal - 1
FROM app_lm_position p
CROSS JOIN LATERAL generate_series(1, p.direct_reports) AS seat(ordinal)
WHERE p.direct_reports IS NOT NULL
  AND p.direct_reports > 0;

ALTER TABLE app_lm_position DROP COLUMN direct_reports;

ALTER TABLE app_lm_position
    ALTER COLUMN team_size TYPE varchar(160) USING team_size::text;

-- Days joins Weeks and Months, so the vocabulary moves to the Java enum as V10 and V21 moved theirs.
ALTER TABLE app_lm_position DROP CONSTRAINT app_lm_position_notice_unit_chk;

-- ── Step 4 · Compensation ────────────────────────────────────────────────────

-- Whether salary_min/salary_max are monthly or annual figures. Without it the same pair of numbers
-- means two things a factor of twelve apart, and every reader — the package total on the screen, the
-- band on the mandate report — has to guess which.
ALTER TABLE app_lm_position
    ADD COLUMN base_salary_mode varchar(16) NOT NULL DEFAULT 'ANNUAL';

ALTER TABLE app_lm_position ADD COLUMN bonus_value numeric(6, 2);
ALTER TABLE app_lm_position ADD COLUMN bonus_basis varchar(24);

UPDATE app_lm_position
SET bonus_value = bonus_target_pct,
    bonus_basis = 'PERCENT_OF_BASE'
WHERE bonus_target_pct IS NOT NULL;

-- Takes V9's app_lm_position_bonus_pct_chk with it: a CHECK belongs to its column.
ALTER TABLE app_lm_position DROP COLUMN bonus_target_pct;

ALTER TABLE app_lm_position ADD COLUMN incentive_type varchar(24);
ALTER TABLE app_lm_position ADD COLUMN incentive_amount bigint;
ALTER TABLE app_lm_position ADD COLUMN incentive_vesting varchar(200);

UPDATE app_lm_position
SET incentive_vesting = ltip
WHERE ltip IS NOT NULL;

ALTER TABLE app_lm_position DROP COLUMN ltip;

-- A benefit was a label. It becomes what a package actually lists: a name, an amount that is often
-- not stated at all, and the period that amount is quoted over. The amount stays nullable — "Annual
-- home leave" with no figure is a real line in a real package, and a zero would assert a number
-- nobody gave us.
ALTER TABLE app_lm_position_benefit RENAME COLUMN label TO name;
ALTER TABLE app_lm_position_benefit ALTER COLUMN name TYPE varchar(120);
ALTER TABLE app_lm_position_benefit ADD COLUMN amount bigint;
ALTER TABLE app_lm_position_benefit
    ADD COLUMN frequency varchar(16) NOT NULL DEFAULT 'MONTHLY';

-- ── Step 5 · Assessment ──────────────────────────────────────────────────────

-- What a competency means for this mandate, beside its weight. A weight on its own is a number the
-- second reader has to interpret.
ALTER TABLE app_lm_position_competency ADD COLUMN description varchar(300);

-- ── Step 6 · Review and publish ──────────────────────────────────────────────

ALTER TABLE app_lm_position ADD COLUMN published_at timestamptz;
ALTER TABLE app_lm_position ADD COLUMN published_by uuid REFERENCES app_lm_user (id);

COMMENT ON COLUMN app_lm_position.published_at IS
    'When the brief was first declared ready. Not a lock: publishing freezes nothing and edits continue.';

-- The foreign-key index V26 and V28 had to add after the fact for the earlier tables.
CREATE INDEX app_lm_position_published_by_idx ON app_lm_position (published_by);

-- ── The position description ─────────────────────────────────────────────────

-- V16 built this table for an LLM auto-fill that was never written, and nothing has ever mapped it in
-- Java. The dropzone this release ships stores the file and reads nothing out of it, so the two
-- columns describing an extraction go rather than being written a status that is not true: COMPLETED
-- would be a lie about a document nothing has opened, and a third value would be a status for a
-- pipeline that does not exist. Extraction will bring its own migration, wanting columns V16 could
-- not have guessed. The table is empty in every environment, so nothing is lost.
ALTER TABLE app_lm_position_document DROP COLUMN extraction_status;
ALTER TABLE app_lm_position_document DROP COLUMN extraction_error;

-- A zero-byte upload is a failed transfer, not a document.
ALTER TABLE app_lm_position_document
    ADD CONSTRAINT app_lm_position_document_size_chk CHECK (file_size > 0);

CREATE INDEX app_lm_position_document_uploaded_by_idx ON app_lm_position_document (uploaded_by);
