/**
 * The comparators a client-sorted grid's columns declare.
 *
 * <p>Each answers only the ascending question: TanStack applies the direction by reversing the
 * ascending result rather than sorting again, so a comparator that tried to be direction-aware would
 * be applied twice.
 *
 * <p>A blank sorts as the largest value, which puts "not set" at the end of an ascending page and the
 * head of a descending one — the two places a reader looks for it.
 */

export function compareText(left: string | null | undefined, right: string | null | undefined): number {
  if (!left) return right ? 1 : 0;
  if (!right) return -1;
  // localeCompare, or "Zeta" sorts before "apple".
  return left.localeCompare(right);
}

export function compareNumber(left: number | null | undefined, right: number | null | undefined): number {
  if (left === null || left === undefined) return right === null || right === undefined ? 0 : 1;
  if (right === null || right === undefined) return -1;
  return left - right;
}

/** ISO dates compare as strings, so an undated row only has to be given a date no row can reach. */
export function compareDate(left: string | null | undefined, right: string | null | undefined): number {
  return compareText(left || "9999-12-31", right || "9999-12-31");
}
