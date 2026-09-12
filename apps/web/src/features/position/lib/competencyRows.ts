import type { Competency } from "../api/types";

/**
 * A competency while the wizard is editing it, carrying an id the API knows nothing about.
 *
 * <p>Both new affordances on step five need to name a row: a lock has to survive the row moving, and
 * a sortable list needs a stable key per item. An array index is neither — reordering and removing
 * both shift it, so a lock would silently jump to whichever competency inherited the slot, and dnd-kit
 * mis-drops when items are keyed by position.
 *
 * <p>So the id is minted here, lives only in component state, and is stripped before the array is
 * sent. The wire shape is unchanged, and no lock ever needs remapping.
 */
export interface IdentifiedCompetency extends Competency {
  id: string;
}

export function identify(rows: Competency[]): IdentifiedCompetency[] {
  return rows.map((row) => ({ ...row, id: crypto.randomUUID() }));
}

/** Drops the client-side id on the way to the API, which has no column for it. */
export function forWire(rows: IdentifiedCompetency[]): Competency[] {
  return rows.map(({ id: _id, ...competency }) => competency);
}

/**
 * Unpacks a proposed competency's `"<name> — <weight> — <description>"` value — the same em-dash
 * convention `PositionPage.tsx`'s `benefitFrom` uses for a benefit's name and frequency, extended to
 * a third attribute. `PositionAssessmentProposer` never proposes a description without also packing
 * a weight segment ahead of it (defaulting to "0" when the document gave it none), so the second
 * segment, when present, is always the weight — splitting on the separator and reading positionally
 * is unambiguous.
 */
export function competencyFrom(value: string): Competency {
  const [name = value, weightToken, ...rest] = value.split(" — ");
  const weight = Number(weightToken);
  return {
    name,
    weight: Number.isFinite(weight) ? weight : 0,
    description: rest.length > 0 ? rest.join(" — ") : null,
  };
}

/**
 * Moves the row with {@code fromId} to where {@code toId} currently sits, which is what a sortable
 * drop means. Order is the ranking, so this is the whole of what reordering does.
 */
export function moveRow(
  rows: IdentifiedCompetency[],
  fromId: string,
  toId: string,
): IdentifiedCompetency[] {
  const from = rows.findIndex((row) => row.id === fromId);
  const to = rows.findIndex((row) => row.id === toId);
  if (from < 0 || to < 0 || from === to) return rows;

  const next = [...rows];
  const [moved] = next.splice(from, 1);
  next.splice(to, 0, moved);
  return next;
}

/** Adds or removes an id, which is all a lock toggle is. */
export function toggle(locked: ReadonlySet<string>, id: string): Set<string> {
  const next = new Set(locked);
  if (!next.delete(id)) {
    next.add(id);
  }
  return next;
}
