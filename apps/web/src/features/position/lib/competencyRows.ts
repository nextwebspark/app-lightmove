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
