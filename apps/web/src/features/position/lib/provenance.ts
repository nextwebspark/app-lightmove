import type { FieldSource } from "../api/types";

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
