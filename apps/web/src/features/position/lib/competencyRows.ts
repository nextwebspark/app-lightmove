import type { Competency } from "../api/types";

/**
 * A competency being edited, with a client-only id (stripped before sending): an array index shifts on reorder or
 * remove, so a lock would jump rows and dnd-kit mis-drops.
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
