import type { FieldSource, Position } from "../api/types";

/** The Role Brief's own `fieldSources` keys — mirrors the backend's `PositionFieldKeys.DETAILS`. */
export const DETAILS_FIELD_KEYS: ReadonlySet<string> = new Set([
  "department",
  "locationCity",
  "locationCountry",
  "employmentType",
  "seniority",
  "narrative",
]);

/** The mandate context's own `fieldSources` keys — mirrors `PositionFieldKeys.CONTEXT`. */
export const CONTEXT_FIELD_KEYS: ReadonlySet<string> = new Set(["mandateReason", "businessDriver"]);

/** The reporting structure's own `fieldSources` keys — mirrors `PositionFieldKeys.REPORTING`. */
export const REPORTING_FIELD_KEYS: ReadonlySet<string> = new Set(["teamSize", "noticeValue", "noticeUnit"]);

/**
 * Marks the given keys `MANUAL` in a step's `fieldSources` map — what a page calls whenever a person
 * edits a scalar field, so an autosave never sends a stale `TEMPLATE`/`DOCUMENT` claim for a value
 * they just typed over.
 */
export function markManual(
  fieldSources: Record<string, FieldSource>,
  keys: readonly string[],
): Record<string, FieldSource> {
  const next = { ...fieldSources };
  for (const key of keys) {
    next[key] = "MANUAL";
  }
  return next;
}

/**
 * `markManual`, narrowed to whichever keys of a patch this step actually tracks provenance for — a
 * patch can also carry a field with no `fieldSources` entry of its own (`roleTitle`, `responsibilities`,
 * `orgChart`), and stamping those would hand the server a key its allow-list refuses
 * (`PositionFieldKeys.requireKnown`).
 */
export function markManualFrom(
  fieldSources: Record<string, FieldSource>,
  patch: Record<string, unknown>,
  trackedKeys: ReadonlySet<string>,
): Record<string, FieldSource> {
  return markManual(
    fieldSources,
    Object.keys(patch).filter((key) => trackedKeys.has(key)),
  );
}

/**
 * Whether nothing on the brief is a person's own writing — the one condition under which a template
 * may be applied without asking, since applying one replaces responsibilities, the org chart, the
 * competencies and the benefits wholesale, typed rows included. An absent `fieldSources` key is
 * unclaimed rather than edited; a row with no `source` counts as typed, as `documentFill` reads it.
 *
 * <p>Blind to what carries no provenance at all: the role title (never renamed by the automatic path)
 * and compensation's scalars.
 */
export function isUntouched(brief: Position): boolean {
  const maps = [brief.details.fieldSources, brief.context.fieldSources, brief.reporting.fieldSources];
  if (maps.some((sources) => Object.values(sources).includes("MANUAL"))) return false;

  const rows: { source?: FieldSource }[] = [
    ...brief.details.responsibilities,
    ...brief.context.strategicPriorities,
    ...brief.reporting.orgChart,
    ...brief.assessment.criteria,
    ...brief.assessment.technical,
    ...brief.assessment.behavioural,
    ...brief.compensation.benefits,
  ];
  return rows.every((row) => (row.source ?? "MANUAL") !== "MANUAL");
}
