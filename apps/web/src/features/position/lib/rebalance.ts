import type { Competency } from "../api/types";

/**
 * Sets one competency's weight and absorbs the difference into the others, so a panel that already
 * totals 100 stays there.
 *
 * <p>Locked rows are excluded from that absorption: a consultant who has settled one weight can then
 * tune the rest without watching the settled one drift. Everything else redistributes in proportion to
 * its current weight, or equally when those are all zero.
 *
 * <p>A row raises itself out of the panel's headroom first and only takes from the others once the
 * total would pass 100; releasing weight below 100 lowers the total rather than inflating rows nobody
 * touched. Redistribution is what holds a *balanced* panel balanced — a half-built one is left to be
 * built.
 */
export function rebalance<T extends Competency>(
  rows: T[],
  index: number,
  newWeight: number,
  locked: ReadonlySet<number> = new Set(),
): T[] {
  if (rows.length === 0) return rows;

  const total = rows.reduce((sum, row) => sum + row.weight, 0);
  const lockedSum = rows.reduce(
    (sum, row, i) => (i !== index && locked.has(i) ? sum + row.weight : sum),
    0,
  );
  // The dragged row may take any headroom the locked rows are not already holding.
  const ceiling = Math.max(0, 100 - lockedSum);
  const target = Math.max(0, Math.min(ceiling, Math.round(newWeight)));
  const delta = target - rows[index].weight;
  if (delta === 0) return rows;

  const pool = rows.map((_, i) => i).filter((i) => i !== index && !locked.has(i));
  const next = rows.map((row) => ({ ...row }));
  next[index].weight = target;
  // Nothing may move, so the dragged row simply takes its ceiling and the total follows it.
  if (pool.length === 0) return next;

  const poolSum = pool.reduce((sum, i) => sum + next[i].weight, 0);
  const absorbed =
    delta > 0
      ? Math.min(Math.max(0, delta - (100 - total)), poolSum)
      : total >= 100
        ? delta
        : 0;

  if (absorbed !== 0) {
    const share = absorbed / pool.length;
    for (const i of pool) {
      next[i].weight =
        poolSum > 0
          ? Math.max(0, next[i].weight - absorbed * (next[i].weight / poolSum))
          : next[i].weight - share;
    }
  }

  for (const i of pool) next[i].weight = Math.round(next[i].weight);
  // Reconciled against the total this move actually reached, not the one it started from: a row
  // taking headroom leaves the others alone and the panel legitimately climbs. The remainder lands on
  // a row that is allowed to move — a locked one would undo its lock by a fraction at a time.
  const achieved = total + delta - absorbed;
  const drift = achieved - next.reduce((sum, row) => sum + row.weight, 0);
  if (drift !== 0) {
    next[pool[0]].weight = Math.max(0, next[pool[0]].weight + drift);
  }
  return next;
}
