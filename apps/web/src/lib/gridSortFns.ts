/**
 * Comparators for client-sorted grids. Ascending only — TanStack reverses for descending, so a direction-aware one
 * would apply twice. A blank sorts largest: last ascending, first descending.
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
