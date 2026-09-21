import type {
  BaseSalaryMode,
  BenefitFrequency,
  BonusBasis,
  EmploymentType,
  IncentiveType,
  MandateReason,
} from "../api/types";

/**
 * Every enum the Position screen renders, spelled the way the brief's mockups spell it. One place, so
 * a step's chips, the rail and the review cards can never disagree about a value's name.
 */

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME_PERMANENT: "Full-time",
  PART_TIME: "Part-time",
  FIXED_TERM_CONTRACT: "Contract",
  TEMPORARY: "Temporary",
  INTERIM: "Interim",
  RETAINED_ADVISORY: "Advisory",
};

/** The five the Role Brief offers, in its order; a stored `RETAINED_ADVISORY` is shown as recorded. */
export const OFFERED_EMPLOYMENT_TYPES: readonly EmploymentType[] = [
  "FULL_TIME_PERMANENT",
  "PART_TIME",
  "FIXED_TERM_CONTRACT",
  "TEMPORARY",
  "INTERIM",
];

// Seniority is not this screen's to define: a brief and a candidate are written in one ladder.
export { SENIORITY_LABELS } from "../../../lib/seniority";

export const MANDATE_REASON_LABELS: Record<MandateReason, string> = {
  SUCCESSION: "Succession plan",
  NEW_ROLE: "New position",
  BACKFILL: "Replacement",
  GROWTH_EXPANSION: "Growth",
  RESTRUCTURING: "Restructure",
};

export const BASE_SALARY_MODE_LABELS: Record<BaseSalaryMode, string> = {
  ANNUAL: "Annual",
  MONTHLY: "Monthly",
};

export const BONUS_BASIS_LABELS: Record<BonusBasis, string> = {
  PERCENT_OF_BASE: "% of base",
  FIXED_AMOUNT: "Fixed amount",
  PERCENT_OF_TOTAL_FIXED: "% of total fixed",
  MONTHS_OF_BASE: "Months of base",
};

/** The two the Compensation step offers; the other two bases are shown only where already stored. */
export const OFFERED_BONUS_BASES: readonly BonusBasis[] = ["PERCENT_OF_BASE", "FIXED_AMOUNT"];

export const INCENTIVE_TYPE_LABELS: Record<IncentiveType, string> = {
  OPTIONS: "Stock Options",
  RSU: "RSUs",
  LTIP_CASH: "Cash",
  PHANTOM_EQUITY: "Phantom equity",
};

/** The three the Compensation step offers beside None; phantom equity is shown only where stored. */
export const OFFERED_INCENTIVE_TYPES: readonly IncentiveType[] = ["OPTIONS", "RSU", "LTIP_CASH"];

export const BENEFIT_FREQUENCY_LABELS: Record<BenefitFrequency, string> = {
  MONTHLY: "Monthly",
  YEARLY: "Yearly",
};

/** The currencies the mockup's picker offers, in its order (GCC first, then the reference pair). */
export const CURRENCIES = ["AED", "USD", "SAR", "QAR", "KWD", "GBP", "EUR"] as const;

export function labelOf<T extends string>(
  labels: Record<T, string>,
  value: T | null | undefined,
): string | null {
  return value ? labels[value] : null;
}
