import type { NumericRange, StrategyFilter } from "../api/types";

/** Both ends typed and the low one above the high: a range the server refuses whole. */
export function isInverted(range: NumericRange | null): boolean {
  return range !== null && range.min !== null && range.max !== null && range.min > range.max;
}

/**
 * What the autosave sends: an inverted range goes as no range, never as itself. Sent, the server's 400 kept the
 * whole filter unsaved, and every later chip click carried the same range into the same refusal — with no second
 * toast — while the grid kept showing the old scope under the new chips.
 */
export function savableFilter(filter: StrategyFilter): StrategyFilter {
  if (!isInverted(filter.employeeRange) && !isInverted(filter.revenueRange)) return filter;
  return {
    ...filter,
    employeeRange: isInverted(filter.employeeRange) ? null : filter.employeeRange,
    revenueRange: isInverted(filter.revenueRange) ? null : filter.revenueRange,
  };
}
