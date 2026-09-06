import type { Competency } from "../api/types";

/**
 * Sets one competency's weight and absorbs the difference into the others, so a panel that already
 * totals 100 stays there.
 *
 * <p>Locked rows are excluded from that absorption: a consultant who has settled one weight can then
 * tune the rest without watching the settled one drift. Everything else redistributes in proportion to
 * its current weight, or equally when those are all zero.
 *
 * <p>A row raises itself out of the panel's headroom first, and only takes from the others once the
 * total would pass 100. Conserving the current total instead froze every slider in the two states a
 * panel is built in — a panel rebuilt from empty (all rows at 0, so there was nothing to move) and
 * one whose locked rows already held the whole total — and, once unfrozen, made each row somebody
 * built up rob the one before it.
 */
export function rebalance<T extends Competency>(
  rows: T[],
  index: number,
  newWeight: number,
  locked: ReadonlySet<number> = new Set(),
): T[] {
  if (rows.length === 0) return rows;

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

  const poolSum = pool.reduce((sum, i) => sum + next[i].weight, 0);
  let absorbed = 0;
  if (pool.length === 0) {
    absorbed = 0;
  } else if (delta > 0) {
    const headroom = Math.max(0, 100 - rows.reduce((sum, row) => sum + row.weight, 0));
    absorbed = Math.min(Math.max(0, delta - headroom), poolSum);
    for (const i of pool) {
      if (poolSum > 0) {
        next[i].weight = Math.max(0, next[i].weight - absorbed * (next[i].weight / poolSum));
      }
    }
  } else {
    absorbed = delta;
    const share = delta / pool.length;
    for (const i of pool) {
      next[i].weight =
        poolSum > 0 ? next[i].weight - delta * (next[i].weight / poolSum) : next[i].weight - share;
    }
  }

  for (const i of pool) next[i].weight = Math.round(next[i].weight);
  // Reconciled against the total this move actually reached, not the one it started from: a row
  // taking headroom leaves the others alone and the panel legitimately climbs. The remainder lands on
  // a row that is allowed to move — a locked one would undo its lock by a fraction at a time.
  const achieved = rows.reduce((sum, row) => sum + row.weight, 0) + delta - absorbed;
  const drift = achieved - next.reduce((sum, row) => sum + row.weight, 0);
  if (drift !== 0 && pool.length > 0) {
    next[pool[0]].weight = Math.max(0, next[pool[0]].weight + drift);
  }
  return next;
}
