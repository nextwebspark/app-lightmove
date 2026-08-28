import type { Compensation } from "../api/types";

/**
 * The package maths behind step four: what the base, bonus, incentive and allowances add up to over
 * a year, and how that total divides.
 *
 * Every figure on the brief carries the unit it is quoted in — the base its period, the bonus its
 * basis, each allowance its frequency — so annualising is arithmetic rather than guesswork. The
 * mockup's own script applies "% of base" to every bonus regardless of the basis its select offers;
 * the three bases are honoured here, because a bonus quoted in months is not a percentage.
 */

export interface PackageTotal {
  /** Null when no base band has been entered — there is nothing to total yet. */
  min: number | null;
  max: number | null;
  base: { min: number; max: number };
  bonus: { min: number; max: number };
  incentive: number;
  allowances: number;
}

/** One legend row under the mix bar: its share of the largest total, as a percentage. */
export interface PackageMixRow {
  label: string;
  amount: number;
  percent: number;
}

const MONTHS_PER_YEAR = 12;

export function annualisedBase(compensation: Compensation): { min: number; max: number } {
  const multiplier = compensation.baseSalaryMode === "MONTHLY" ? MONTHS_PER_YEAR : 1;
  return {
    min: (compensation.salaryMin ?? 0) * multiplier,
    max: (compensation.salaryMax ?? 0) * multiplier,
  };
}

export function annualisedAllowances(compensation: Compensation): number {
  return compensation.benefits.reduce((total, benefit) => {
    const amount = benefit.amount ?? 0;
    return total + (benefit.frequency === "YEARLY" ? amount : amount * MONTHS_PER_YEAR);
  }, 0);
}

/**
 * The bonus in money, for a given annualised base. A percentage basis scales with the base; months
 * of base is that many months of it, which is why the base's own period cannot be ignored.
 */
export function bonusOn(compensation: Compensation, annualBase: number): number {
  const value = compensation.bonusValue ?? 0;
  if (value <= 0 || annualBase <= 0) return 0;
  switch (compensation.bonusBasis) {
    case "MONTHS_OF_BASE":
      return (annualBase / MONTHS_PER_YEAR) * value;
    case "PERCENT_OF_TOTAL_FIXED":
      return ((annualBase + annualisedAllowances(compensation)) * value) / 100;
    case "PERCENT_OF_BASE":
      return (annualBase * value) / 100;
    default:
      // No basis chosen: a bare number means nothing yet, so it contributes nothing.
      return 0;
  }
}

export function packageTotal(compensation: Compensation): PackageTotal {
  const base = annualisedBase(compensation);
  const bonus = { min: bonusOn(compensation, base.min), max: bonusOn(compensation, base.max) };
  const incentive = compensation.incentiveAmount ?? 0;
  const allowances = annualisedAllowances(compensation);
  const hasBand = base.min > 0 || base.max > 0;

  return {
    min: hasBand ? base.min + bonus.min + incentive + allowances : null,
    max: hasBand ? base.max + bonus.max + incentive + allowances : null,
    base,
    bonus,
    incentive,
    allowances,
  };
}

/**
 * The stacked bar under the total. Shares are of the top of the band, so the bar reads as "what the
 * best case is made of" — and a component with any money in it never renders as an invisible sliver.
 */
export function packageMix(total: PackageTotal): PackageMixRow[] {
  const ceiling = total.max ?? 0;
  const rows = [
    { label: "Base", amount: total.base.max },
    { label: "Bonus", amount: total.bonus.max },
    { label: "LTIP & allowances", amount: total.incentive + total.allowances },
  ];
  return rows.map((row) => ({
    ...row,
    percent: ceiling > 0 && row.amount > 0 ? Math.max(1, Math.round((row.amount / ceiling) * 100)) : 0,
  }));
}

/** The band strip's three readings. Null throughout until both bounds are entered. */
export function bandReadings(compensation: Compensation): {
  min: number | null;
  mid: number | null;
  max: number | null;
} {
  const { salaryMin, salaryMax } = compensation;
  if (salaryMin === null || salaryMax === null) return { min: salaryMin, mid: null, max: salaryMax };
  return { min: salaryMin, mid: Math.round((salaryMin + salaryMax) / 2), max: salaryMax };
}
