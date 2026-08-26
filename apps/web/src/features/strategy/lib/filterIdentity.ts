import type { NumericRange, StrategyFilter } from "../api/types";

/**
 * Whether two filters select the same thing — what marks a saved search as the one currently on
 * screen.
 *
 * The comparison mirrors the server's own normalisation rather than the literal documents: token
 * lists are sets, so the order chips were clicked in is not meaning, and a range of two nulls is the
 * absence of a range, which is how `StrategyFilter`'s compact constructor stores it. Comparing the
 * raw JSON instead would leave a search that was just loaded looking inactive.
 */
export function sameFilter(a: StrategyFilter, b: StrategyFilter): boolean {
  return (
    sameTokens(a.industries, b.industries) &&
    sameTokens(a.keywords, b.keywords) &&
    sameTokens(a.marketSegments, b.marketSegments) &&
    sameTokens(a.countries, b.countries) &&
    sameTokens(a.employeeBands, b.employeeBands) &&
    sameTokens(a.revenueBands, b.revenueBands) &&
    sameRange(a.employeeRange, b.employeeRange) &&
    sameRange(a.revenueRange, b.revenueRange)
  );
}

function sameTokens(a: string[] | null | undefined, b: string[] | null | undefined): boolean {
  const left = [...(a ?? [])].sort();
  const right = [...(b ?? [])].sort();
  return left.length === right.length && left.every((value, index) => value === right[index]);
}

function sameRange(a: NumericRange | null, b: NumericRange | null): boolean {
  const left = normalise(a);
  const right = normalise(b);
  if (left === null || right === null) return left === right;
  return left.min === right.min && left.max === right.max;
}

/** An open-ended range at both ends constrains nothing, and the server stores it as no range at all. */
function normalise(range: NumericRange | null): NumericRange | null {
  if (!range) return null;
  return range.min === null && range.max === null ? null : range;
}
