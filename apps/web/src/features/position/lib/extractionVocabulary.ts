import type { ExtractionSource, ProposalConfidence } from "../api/types";

/**
 * How a proposal's confidence and source read on screen, in one place — mirrors
 * `triageVocabulary.ts`'s placement and shape, since a confidence badge is the same kind of signal a
 * source badge is: how far a reading is worth trusting before it is accepted.
 */

export const CONFIDENCE_STYLES: Record<ProposalConfidence, { label: string; className: string }> = {
  high: { label: "High", className: "text-green bg-green-dim" },
  medium: { label: "Medium", className: "text-amber bg-amber-dim" },
  low: { label: "Low", className: "text-text2 bg-line-soft" },
};

/** The panel's one-line summary of what produced the whole reading. */
export const EXTRACTION_SOURCE_LABELS: Record<ExtractionSource, string> = {
  model: "read by the assistant",
  documentHeadings: "the assistant could not be reached — read from the document's own headings, so check these",
  // Reached with fields present only via template backfill: this step has no heuristic fallback, so a
  // failed or blocked read still surfaces the matched template's own values, each labelled below.
  none: "no reading — every field below is proposed from the matched template",
};

/** Field keys the panel renders a human label for, in the order they appear. */
export const EXTRACTION_FIELD_LABELS: Record<string, string> = {
  roleTitle: "Role title",
  department: "Department",
  location: "Location",
  employmentType: "Employment type",
  seniority: "Seniority",
  narrative: "Ideal profile",
  responsibility: "Responsibility",
  mandateReason: "Mandate reason",
  businessDriver: "Business driver",
  strategicPriority: "Strategic priority",
  currency: "Currency",
  salaryMin: "Minimum base salary",
  salaryMax: "Maximum base salary",
  baseSalaryMode: "Base salary period",
  bonusValue: "Bonus target",
  bonusBasis: "Bonus basis",
  incentiveType: "Incentive type",
  incentiveAmount: "Incentive amount",
  incentiveVesting: "Vesting schedule",
  benefit: "Benefit",
  requiredCriterion: "Required criterion",
  preferredCriterion: "Preferred criterion",
  technicalCompetency: "Technical competency",
  behaviouralCompetency: "Behavioural competency",
  reportsToTitle: "Reports to",
  directReportTitle: "Direct report",
  teamSize: "Total team size",
  noticePeriod: "Notice period",
};
