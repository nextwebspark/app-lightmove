import type { NumericRange, StrategyFilter } from "../api/types";

/** Both ends typed and the low one above the high: a range the server refuses whole. */
export function isInverted(range: NumericRange | null): boolean {
  return range !== null && range.min !== null && range.max !== null && range.min > range.max;
}

/**
 * The range as it narrows the scope, or null when it narrows nothing: absent, both ends empty (Custom Range picked,
 * no figure typed), or inverted. The one rule the payload, the badge, the rail's pill and the saved-search match read.
 */
export function appliedRange(range: NumericRange | null): NumericRange | null {
  if (range === null || (range.min === null && range.max === null) || isInverted(range)) return null;
  return range;
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
