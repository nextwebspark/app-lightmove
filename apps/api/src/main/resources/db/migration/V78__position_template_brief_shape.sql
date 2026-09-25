-- A role template's body takes the brief's five-step shape.
--
-- The reporting line was a `reportsTo` title and a flat `directReports` list, the shape the brief itself
-- left behind at V39; it becomes `orgChart`, a tree of seats with one flagged as the role — the manager
-- as the root, the role beneath it, each direct report beneath the role, ids the file's own short
-- strings. `department` and `strategicPriorities` go: the brief no longer edits either, so a template
-- drafting them was filling fields nobody sees. `technicalShare` arrives at 50, the brief's own default
-- since V66, so an existing template drafts the split every brief already had.
--
-- Every row moves, the library's and each firm's alike: this is a change of format, not of anybody's
-- content, and a firm's copy left in the old shape would read back with no chart at all. revised_at is
-- left alone for V77's reason — nothing a firm customised is newer or older for it.
UPDATE app_lm_position_template
SET body = (body - 'department' - 'strategicPriorities' - 'reportsTo' - 'directReports')
    || jsonb_build_object(
        'technicalShare', 50,
        'orgChart',
        CASE WHEN coalesce(btrim(body ->> 'reportsTo'), '') <> ''
             THEN jsonb_build_array(jsonb_build_object(
                     'id', 'manager', 'parentId', NULL, 'title', btrim(body ->> 'reportsTo'),
                     'mandateSeat', false))
             ELSE '[]'::jsonb
        END
        || jsonb_build_array(jsonb_build_object(
               'id', 'role',
               'parentId', CASE WHEN coalesce(btrim(body ->> 'reportsTo'), '') <> '' THEN 'manager' END,
               'title', NULL,
               'mandateSeat', true))
        || coalesce((
               SELECT jsonb_agg(jsonb_build_object(
                          'id', 'report-' || report.position, 'parentId', 'role',
                          'title', btrim(report.title), 'mandateSeat', false)
                      ORDER BY report.position)
               FROM jsonb_array_elements_text(
                        CASE WHEN jsonb_typeof(body -> 'directReports') = 'array'
                             THEN body -> 'directReports' ELSE '[]'::jsonb END)
                    WITH ORDINALITY AS report(title, position)
               WHERE btrim(report.title) <> ''), '[]'::jsonb))
WHERE NOT body ? 'orgChart';
