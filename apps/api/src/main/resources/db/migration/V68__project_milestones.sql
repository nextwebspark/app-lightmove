-- What kind of work a mandate is, and when each of its milestones is due.
--
-- target_date has been doing two unrelated jobs. The Position screen's Role Brief writes it as the
-- date the hire should start, while ProjectHealth read the same column as the deadline it judged the
-- mandate against — two dates that are months apart in practice, so every health dot was measured
-- against the wrong one. From here target_date means only what the brief means by it, and health
-- reads the milestones below.
--
-- A mapping-only mandate delivers a universe map and has one milestone: mapping_target_date, which
-- the client knows as the map delivery date. An executive search has two — mapping_target_date is
-- the internal date the map must be finished by, and shortlist_target_date is the shortlist the
-- hiring manager is waiting for.
--
-- The backfill keeps every existing mandate judged against the date it was already judged against:
-- target_date was the only deadline anyone could state, so it becomes the mapping target. A lead who
-- meant something else corrects it on the mandate.
--
-- Except where that date predates the mandate itself. Nothing stopped a brief stating a target start
-- already in the past, and such a row would fail the ordering CHECK below and abort this migration —
-- taking the deploy with it, since the API cannot boot on a half-applied schema. Those mandates get no
-- mapping target rather than an impossible one: health reads them as undated, which is the truth,
-- because that date was never a deadline anybody could still meet.

ALTER TABLE app_lm_project
    ADD COLUMN project_type varchar(32) NOT NULL DEFAULT 'MAPPING'
        CONSTRAINT app_lm_project_type_chk CHECK (project_type IN ('MAPPING', 'EXECUTIVE_SEARCH')),
    ADD COLUMN start_date            date,
    ADD COLUMN mapping_target_date   date,
    ADD COLUMN shortlist_target_date date;

UPDATE app_lm_project
SET start_date          = created_at::date,
    mapping_target_date = CASE WHEN target_date >= created_at::date THEN target_date END;

-- The default goes again immediately (V40's idiom): from here the type is always written explicitly.
ALTER TABLE app_lm_project
    ALTER COLUMN start_date SET NOT NULL,
    ALTER COLUMN project_type DROP DEFAULT;

ALTER TABLE app_lm_project
    ADD CONSTRAINT app_lm_project_shortlist_type_chk
        CHECK (project_type = 'EXECUTIVE_SEARCH' OR shortlist_target_date IS NULL),
    ADD CONSTRAINT app_lm_project_mapping_order_chk
        CHECK (mapping_target_date IS NULL OR mapping_target_date >= start_date),
    ADD CONSTRAINT app_lm_project_shortlist_order_chk
        CHECK (shortlist_target_date IS NULL OR mapping_target_date IS NULL
            OR shortlist_target_date >= mapping_target_date);

COMMENT ON COLUMN app_lm_project.project_type IS
    'MAPPING delivers a universe map and has one milestone; EXECUTIVE_SEARCH runs through to a shortlist and has two.';
COMMENT ON COLUMN app_lm_project.start_date IS
    'When work on the mandate began. The left edge of every window health measures elapsed time across.';
COMMENT ON COLUMN app_lm_project.mapping_target_date IS
    'When mapping is due: the client-facing map delivery date on a MAPPING mandate, the internal mapping target on a search.';
COMMENT ON COLUMN app_lm_project.shortlist_target_date IS
    'When the shortlist is due to the hiring manager. EXECUTIVE_SEARCH only.';
COMMENT ON COLUMN app_lm_project.target_date IS
    'The brief''s target start date — when the hire should begin. Deliberately not a milestone: health never reads it.';

-- The project drawer's activity feed. Every project-domain event already targets ('project', id);
-- V1 indexed the workspace, the actor and the type, but never the target.
CREATE INDEX app_lm_audit_event_target_idx
    ON app_lm_audit_event (target_type, target_id, occurred_at DESC);
