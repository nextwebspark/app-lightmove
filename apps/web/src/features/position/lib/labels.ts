import type {
  BaseSalaryMode,
  BenefitFrequency,
  BonusBasis,
  EmploymentType,
  HiringUrgency,
  IncentiveType,
  MandateReason,
  NoticeUnit,
  StrategicPriority,
} from "../api/types";

/**
 * Every enum the Position screen renders, spelled the way Position.dc.html spells it. One place, so
 * a step's select, the summary rail and the review cards can never disagree about a value's name.
 */

export const EMPLOYMENT_TYPE_LABELS: Record<EmploymentType, string> = {
  FULL_TIME_PERMANENT: "Full-time, permanent",
  FIXED_TERM_CONTRACT: "Full-time, fixed-term",
  PART_TIME: "Part-time",
  INTERIM: "Interim / contract",
  RETAINED_ADVISORY: "Advisory",
};

// Seniority is not this screen's to define: a brief and a candidate are written in one ladder.
export { SENIORITY_LABELS } from "../../../lib/seniority";

export const MANDATE_REASON_LABELS: Record<MandateReason, string> = {
  NEW_ROLE: "New role",
  BACKFILL: "Backfill",
  RESTRUCTURING: "Restructure",
  SUCCESSION: "Succession plan",
  GROWTH_EXPANSION: "Growth / expansion",
};

export const HIRING_URGENCY_LABELS: Record<HiringUrgency, string> = {
  STANDARD: "Standard (90 days)",
  PRIORITY: "Priority (60 days)",
  URGENT: "Urgent (30 days)",
};

export const STRATEGIC_PRIORITY_LABELS: Record<StrategicPriority, string> = {
  CAPITAL_DISCIPLINE: "Capital discipline",
  PORTFOLIO_GROWTH: "Portfolio growth",
  OPERATIONAL_EXCELLENCE: "Operational excellence",
  GOVERNANCE_AND_CONTROLS: "Governance & controls",
  TALENT_DEVELOPMENT: "Talent development",
};

export const NOTICE_UNIT_LABELS: Record<NoticeUnit, string> = {
  MONTHS: "Months",
  WEEKS: "Weeks",
  DAYS: "Days",
};

export const BASE_SALARY_MODE_LABELS: Record<BaseSalaryMode, string> = {
  ANNUAL: "Annual",
  MONTHLY: "Monthly",
};

export const BONUS_BASIS_LABELS: Record<BonusBasis, string> = {
  PERCENT_OF_BASE: "% of base salary",
  PERCENT_OF_TOTAL_FIXED: "% of total fixed",
  MONTHS_OF_BASE: "Months of base salary",
};

export const INCENTIVE_TYPE_LABELS: Record<IncentiveType, string> = {
  LTIP_CASH: "LTIP cash",
  RSU: "RSUs",
  OPTIONS: "Options",
  PHANTOM_EQUITY: "Phantom equity",
};

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
