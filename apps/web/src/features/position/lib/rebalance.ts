import type { Competency } from "../api/types";

/**
 * Sets one competency's weight and redistributes the difference across the others in proportion to
 * their current weights, keeping the panel's total unchanged (the mockup's slider behaviour). When
 * the others sit at zero the difference is shared equally instead. Rounding remainder lands on the
 * first other row so the total is exact, not approximate.
 */
export function rebalance(rows: Competency[], index: number, newWeight: number): Competency[] {
  const target = Math.max(0, Math.min(100, Math.round(newWeight)));
  const delta = target - rows[index].weight;
  if (delta === 0 || rows.length === 0) return rows;

  const next = rows.map((row) => ({ ...row }));
  const others = next.map((_, i) => i).filter((i) => i !== index);
  const othersSum = others.reduce((sum, i) => sum + next[i].weight, 0);

  next[index].weight = target;
  if (othersSum <= 0) {
    const share = others.length ? -delta / others.length : 0;
    for (const i of others) next[i].weight = Math.max(0, next[i].weight + share);
  } else {
    for (const i of others) {
      next[i].weight = Math.max(0, next[i].weight - delta * (next[i].weight / othersSum));
    }
  }

  for (const row of next) row.weight = Math.round(row.weight);
  const total = rows.reduce((sum, row) => sum + row.weight, 0);
  const drift = total - next.reduce((sum, row) => sum + row.weight, 0);
  if (drift !== 0) {
    const fix = others.length ? others[0] : index;
    next[fix].weight = Math.max(0, next[fix].weight + drift);
  }
  return next;
}
