import type { Competency } from "../api/types";

/**
 * Sets one competency's weight and absorbs the difference into the others, keeping the panel's total
 * exactly where it was.
 *
 * <p>Locked rows are excluded from that absorption: a consultant who has settled one weight can then
 * tune the rest without watching the settled one drift. Everything else redistributes in proportion to
 * its current weight, or equally when those are all zero.
 *
 * The total is whatever the panel currently holds, not 100 — a panel is deliberately allowed to sit
 * off-balance while somebody is still typing, and the screen is what reads that back to them.
 */
export function rebalance<T extends Competency>(
  rows: T[],
  index: number,
  newWeight: number,
  locked: ReadonlySet<number> = new Set(),
): T[] {
  if (rows.length === 0) return rows;

  const pool = rows.map((_, i) => i).filter((i) => i !== index && !locked.has(i));
  // Nothing left that may move, so there is nowhere for the difference to go. Refusing to change the
  // dragged row is the only answer that keeps the total honest.
  if (pool.length === 0) return rows;

  const total = rows.reduce((sum, row) => sum + row.weight, 0);
  const lockedSum = rows.reduce((sum, row, i) => (locked.has(i) ? sum + row.weight : sum), 0);

  // The dragged row may only take what the locked rows are not already holding.
  const ceiling = Math.max(0, total - lockedSum);
  const target = Math.max(0, Math.min(ceiling, Math.round(newWeight)));
  const delta = target - rows[index].weight;
  if (delta === 0) return rows;

  const next = rows.map((row) => ({ ...row }));
  next[index].weight = target;

  const poolSum = pool.reduce((sum, i) => sum + next[i].weight, 0);
  if (poolSum <= 0) {
    const share = delta / pool.length;
    for (const i of pool) next[i].weight = Math.max(0, next[i].weight - share);
  } else {
    for (const i of pool) {
      next[i].weight = Math.max(0, next[i].weight - delta * (next[i].weight / poolSum));
    }
  }

  for (const i of pool) next[i].weight = Math.round(next[i].weight);
  // The rounding remainder lands on a row that is allowed to move. Putting it on a locked one would
  // quietly undo the lock a fraction at a time.
  const drift = total - next.reduce((sum, row) => sum + row.weight, 0);
  if (drift !== 0) {
    next[pool[0]].weight = Math.max(0, next[pool[0]].weight + drift);
  }
  return next;
}
