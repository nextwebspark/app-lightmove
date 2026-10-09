import type { ChoiceCardOption } from "../../../components/ui";
import { NOTICE_PERIODS } from "../../../lib/noticePeriod";
import { SENIORITY_LABELS, SENIORITY_TIERS, type SeniorityTier } from "../../../lib/seniority";
import type { BonusBasis, EmploymentType, IncentiveType, MandateReason } from "../api/types";
import {
  BONUS_BASIS_LABELS,
  EMPLOYMENT_TYPE_LABELS,
  INCENTIVE_TYPE_LABELS,
  MANDATE_REASON_LABELS,
  OFFERED_BONUS_BASES,
  OFFERED_EMPLOYMENT_TYPES,
  OFFERED_INCENTIVE_TYPES,
} from "../lib/labels";
import type { ChipOption } from "./BriefFields";

/** The choices the brief offers, shared with the role-template editor so a template offers the same. */

export const EMPLOYMENT_OPTIONS: ChipOption<EmploymentType>[] = OFFERED_EMPLOYMENT_TYPES.map((value) => ({
  value,
  label: EMPLOYMENT_TYPE_LABELS[value],
}));

export const SENIORITY_OPTIONS: ChipOption<SeniorityTier>[] = SENIORITY_TIERS.map((value) => ({
  value,
  label: SENIORITY_LABELS[value],
}));

export const REASON_OPTIONS: ChipOption<MandateReason>[] = (
  Object.entries(MANDATE_REASON_LABELS) as [MandateReason, string][]
).map(([value, label]) => ({ value, label }));

/** The four the brief offers — "None" is the executive's claim, not a period a mandate plans for. */
export const NOTICE_OPTIONS: ChipOption<string>[] = NOTICE_PERIODS.filter((period) => period.months > 0).map(
  (period) => ({ value: period.label, label: period.label }),
);

export type ConfidentialityLevel = "standard" | "confidential";

export const CONFIDENTIALITY_OPTIONS: readonly ChoiceCardOption<ConfidentialityLevel>[] = [
  { value: "standard", title: "Standard", body: "Visible to the whole workspace" },
  { value: "confidential", title: "Confidential", body: "Restricted until shortlist" },
];

export const BONUS_OPTIONS: ChipOption<BonusBasis>[] = OFFERED_BONUS_BASES.map((value) => ({
  value,
  label: BONUS_BASIS_LABELS[value],
}));

/** "None" is the absence of an incentive, which the wire spells as null rather than a fourth kind. */
export type IncentiveChoice = IncentiveType | "NONE";

export const INCENTIVE_OPTIONS: ChipOption<IncentiveChoice>[] = [
  ...OFFERED_INCENTIVE_TYPES.map((value) => ({ value, label: INCENTIVE_TYPE_LABELS[value] })),
  { value: "NONE", label: "None" },
];
