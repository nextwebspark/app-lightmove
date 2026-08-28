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

-- The org chart is a tree of seats, and exactly one of them is the mandate's own. That single flag is
-- what makes the rest fall out: "reports to" is the parent of the mandate seat, "direct reports" are
-- its children, and a chart that wants a skip-level, a second-line manager or a grandchild just adds
-- nodes rather than needing a fourth concept. It replaces two columns (reports_to, reports_to_name)
-- and the fixed tier they described.
--
-- parent_node_id carries no foreign key on purpose. The whole chart is replaced by one write of its
-- step, so a self-referencing key would force delete-then-insert into a dependency order that buys
-- nothing here: the service validates that every parent resolves inside the same position and that
-- the result is a tree, which is the rule an FK could not have expressed anyway.
--
-- canvas_x/canvas_y are where the consultant dragged the box. Nullable, because a node that has never
-- been arranged is laid out from the tree instead — presentation the screen can always recompute, not
-- a fact about the role.
CREATE TABLE app_lm_position_org_node (
    position_id    uuid         NOT NULL REFERENCES app_lm_position (id) ON DELETE CASCADE,
    sort_order     integer      NOT NULL,
    node_id        uuid         NOT NULL,
    parent_node_id uuid,
    title          varchar(160),
    name           varchar(160),
    mandate_seat   boolean      NOT NULL DEFAULT false,
    canvas_x       real,
    canvas_y       real,
    PRIMARY KEY (position_id, sort_order)
);

COMMENT ON TABLE app_lm_position_org_node IS
    'The org chart around the mandate. One row per seat; exactly one carries mandate_seat. Either name field may be blank: a mandate knows the seat long before the person.';

CREATE UNIQUE INDEX app_lm_position_org_node_uk
    ON app_lm_position_org_node (position_id, node_id);

-- Exactly one mandate seat per chart, enforced where it cannot be forgotten — the same shape as
-- V5's one-lead-per-project index.
CREATE UNIQUE INDEX app_lm_position_org_node_seat_uk
    ON app_lm_position_org_node (position_id) WHERE mandate_seat;

-- Every existing brief becomes a chart: the manager it named, the mandate's own seat beneath, and one
-- unfilled seat under that for each direct report it counted. Nothing anyone typed is dropped, and a
-- seat with neither a title nor a name clears itself the next time the step is saved.
INSERT INTO app_lm_position_org_node
    (position_id, sort_order, node_id, parent_node_id, title, name, mandate_seat)
SELECT p.id, 0, seat.manager_id, NULL, p.reports_to, p.reports_to_name, false
FROM app_lm_position p
CROSS JOIN LATERAL (SELECT gen_random_uuid() AS manager_id) AS seat;

INSERT INTO app_lm_position_org_node
    (position_id, sort_order, node_id, parent_node_id, title, name, mandate_seat)
SELECT p.id, 1, gen_random_uuid(), manager.node_id, NULL, NULL, true
FROM app_lm_position p
JOIN app_lm_position_org_node manager
  ON manager.position_id = p.id AND manager.sort_order = 0;

INSERT INTO app_lm_position_org_node
    (position_id, sort_order, node_id, parent_node_id, title, name, mandate_seat)
SELECT p.id, 1 + report.ordinal, gen_random_uuid(), seat.node_id, NULL, NULL, false
FROM app_lm_position p
JOIN app_lm_position_org_node seat
  ON seat.position_id = p.id AND seat.mandate_seat
CROSS JOIN LATERAL generate_series(1, p.direct_reports) AS report(ordinal)
WHERE p.direct_reports IS NOT NULL
  AND p.direct_reports > 0;

ALTER TABLE app_lm_position DROP COLUMN direct_reports;
ALTER TABLE app_lm_position DROP COLUMN reports_to;
ALTER TABLE app_lm_position DROP COLUMN reports_to_name;

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
