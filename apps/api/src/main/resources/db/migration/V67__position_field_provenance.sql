-- Every field a brief carries learns where it came from: TEMPLATE, DOCUMENT, or MANUAL.
--
-- Epic #393 retires the "Read from document" review-then-accept panel in favour of filling fields
-- silently and marking what filled them. This is the ground that mark stands on — nothing reads or
-- writes DOCUMENT yet (that starts in #396), but every row and every scalar the fill engine will
-- eventually touch needs somewhere to record it first.
--
-- Two mechanisms. Owned-list rows (responsibilities, priorities, org seats, competencies, criteria,
-- benefits) each carry their own `source` column, the V34/V54 CHECK idiom. The eleven scalars a brief
-- holds outside those lists — the ones a document reading or a template can plausibly claim — are
-- tracked in one jsonb map on the position row itself, `field_sources`, keyed by wire field name. An
-- absent key means nobody has claimed that field; it stays fillable. Compensation and the role title
-- are deliberately not in the map: step 4 is never read by the document extractor, and the title is
-- never auto-filled — there is nothing to claim.
--
-- app_lm_position_criterion.from_brief folds into the same `source` column everything else gets:
-- true becomes TEMPLATE, false becomes MANUAL, and the boolean goes. A template row has never been
-- able to say anything but "this is mine", so this loses no information.
--
-- Backfill is keyed on `version`, not on which columns happen to be non-null. A brief nobody has
-- saved since seeding (version = 0) is the template's, so its rows and the six scalars a template
-- actually writes (department, employment_type, seniority, narrative, notice_value, notice_unit —
-- never location, mandate_reason, business_driver or team_size, which PositionTemplateApplier always
-- leaves alone) become TEMPLATE. A saved brief (version > 0) is somebody's: every row and every
-- non-null scalar of all ten becomes MANUAL, because a legacy row cannot be attributed field by
-- field, and nothing already on screen may start looking like an untouched template default.
--
-- One statement per table, no temp tables — lm_migrate has no TEMPORARY privilege (see V60).

ALTER TABLE app_lm_position
    ADD COLUMN field_sources jsonb NOT NULL DEFAULT '{}';

-- ── Owned-list rows: one source column each ──────────────────────────────────

ALTER TABLE app_lm_position_responsibility ADD COLUMN source varchar(16);
ALTER TABLE app_lm_position_priority       ADD COLUMN source varchar(16);
ALTER TABLE app_lm_position_org_node       ADD COLUMN source varchar(16);
ALTER TABLE app_lm_position_competency     ADD COLUMN source varchar(16);
ALTER TABLE app_lm_position_benefit        ADD COLUMN source varchar(16);

UPDATE app_lm_position_responsibility target
SET source = CASE WHEN position.version = 0 THEN 'TEMPLATE' ELSE 'MANUAL' END
FROM app_lm_position position
WHERE position.id = target.position_id;

UPDATE app_lm_position_priority target
SET source = CASE WHEN position.version = 0 THEN 'TEMPLATE' ELSE 'MANUAL' END
FROM app_lm_position position
WHERE position.id = target.position_id;

UPDATE app_lm_position_org_node target
SET source = CASE WHEN position.version = 0 THEN 'TEMPLATE' ELSE 'MANUAL' END
FROM app_lm_position position
WHERE position.id = target.position_id;

UPDATE app_lm_position_competency target
SET source = CASE WHEN position.version = 0 THEN 'TEMPLATE' ELSE 'MANUAL' END
FROM app_lm_position position
WHERE position.id = target.position_id;

UPDATE app_lm_position_benefit target
SET source = CASE WHEN position.version = 0 THEN 'TEMPLATE' ELSE 'MANUAL' END
FROM app_lm_position position
WHERE position.id = target.position_id;

ALTER TABLE app_lm_position_responsibility ALTER COLUMN source SET NOT NULL;
ALTER TABLE app_lm_position_priority       ALTER COLUMN source SET NOT NULL;
ALTER TABLE app_lm_position_org_node       ALTER COLUMN source SET NOT NULL;
ALTER TABLE app_lm_position_competency     ALTER COLUMN source SET NOT NULL;
ALTER TABLE app_lm_position_benefit        ALTER COLUMN source SET NOT NULL;

ALTER TABLE app_lm_position_responsibility
    ADD CONSTRAINT app_lm_position_responsibility_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));
ALTER TABLE app_lm_position_priority
    ADD CONSTRAINT app_lm_position_priority_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));
ALTER TABLE app_lm_position_org_node
    ADD CONSTRAINT app_lm_position_org_node_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));
ALTER TABLE app_lm_position_competency
    ADD CONSTRAINT app_lm_position_competency_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));
ALTER TABLE app_lm_position_benefit
    ADD CONSTRAINT app_lm_position_benefit_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));

COMMENT ON COLUMN app_lm_position_responsibility.source IS
    'Where this line came from: TEMPLATE, DOCUMENT, or MANUAL (typed).';
COMMENT ON COLUMN app_lm_position_priority.source IS
    'Where this priority came from: TEMPLATE, DOCUMENT, or MANUAL (typed).';
COMMENT ON COLUMN app_lm_position_org_node.source IS
    'Where this seat came from: TEMPLATE, DOCUMENT, or MANUAL (typed).';
COMMENT ON COLUMN app_lm_position_competency.source IS
    'Where this competency came from: TEMPLATE, DOCUMENT, or MANUAL (typed).';
COMMENT ON COLUMN app_lm_position_benefit.source IS
    'Where this benefit came from: TEMPLATE, DOCUMENT, or MANUAL (typed).';

-- ── app_lm_position_criterion: from_brief folds into the same source column ─

ALTER TABLE app_lm_position_criterion ADD COLUMN source varchar(16);

UPDATE app_lm_position_criterion
SET source = CASE WHEN from_brief THEN 'TEMPLATE' ELSE 'MANUAL' END;

ALTER TABLE app_lm_position_criterion ALTER COLUMN source SET NOT NULL;
ALTER TABLE app_lm_position_criterion
    ADD CONSTRAINT app_lm_position_criterion_source_chk CHECK (source IN ('TEMPLATE', 'DOCUMENT', 'MANUAL'));
ALTER TABLE app_lm_position_criterion DROP COLUMN from_brief;

COMMENT ON COLUMN app_lm_position_criterion.source IS
    'Where this criterion came from: TEMPLATE, DOCUMENT, or MANUAL (typed). Replaces the old from_brief boolean.';

-- ── app_lm_position.field_sources: the eleven scalars a template or a document can claim ──────

UPDATE app_lm_position
SET field_sources = (
    CASE WHEN version = 0 THEN
        -- Only what PositionTemplateApplier actually writes today — never either location half,
        -- mandate_reason, business_driver or team_size, which it always leaves as it found them.
        (CASE WHEN department IS NOT NULL THEN jsonb_build_object('department', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN employment_type IS NOT NULL THEN jsonb_build_object('employmentType', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN seniority IS NOT NULL THEN jsonb_build_object('seniority', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN narrative IS NOT NULL THEN jsonb_build_object('narrative', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN notice_value IS NOT NULL THEN jsonb_build_object('noticeValue', 'TEMPLATE') ELSE '{}' END) ||
        (CASE WHEN notice_unit IS NOT NULL THEN jsonb_build_object('noticeUnit', 'TEMPLATE') ELSE '{}' END)
    ELSE
        (CASE WHEN department IS NOT NULL THEN jsonb_build_object('department', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN location_city IS NOT NULL THEN jsonb_build_object('locationCity', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN location_country IS NOT NULL THEN jsonb_build_object('locationCountry', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN employment_type IS NOT NULL THEN jsonb_build_object('employmentType', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN seniority IS NOT NULL THEN jsonb_build_object('seniority', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN narrative IS NOT NULL THEN jsonb_build_object('narrative', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN mandate_reason IS NOT NULL THEN jsonb_build_object('mandateReason', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN business_driver IS NOT NULL THEN jsonb_build_object('businessDriver', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN team_size IS NOT NULL THEN jsonb_build_object('teamSize', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN notice_value IS NOT NULL THEN jsonb_build_object('noticeValue', 'MANUAL') ELSE '{}' END) ||
        (CASE WHEN notice_unit IS NOT NULL THEN jsonb_build_object('noticeUnit', 'MANUAL') ELSE '{}' END)
    END
);

ALTER TABLE app_lm_position ALTER COLUMN field_sources DROP DEFAULT;

COMMENT ON COLUMN app_lm_position.field_sources IS
    'Provenance of the eleven scalars a template or a document reading can claim, keyed by wire field name. An absent key means nobody has claimed the field yet.';
