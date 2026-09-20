import type { FieldSource } from "../api/types";

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
