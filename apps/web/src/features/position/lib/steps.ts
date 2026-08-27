import { formatDate, formatInstantDate } from "../../../lib/format";
import type { Position } from "../api/types";
import { packageTotal } from "./compensation";
import {
  HIRING_URGENCY_LABELS,
  MANDATE_REASON_LABELS,
  SENIORITY_LABELS,
  labelOf,
} from "./labels";

/**
 * The six wizard steps, in one array.
 *
 * Everything that knows about steps reads from here — the summary rail, the Back/Next footer, the
 * review cards and the completion percentage — so a step's name, its heading and the rule for
 * calling it done are stated exactly once. Duplicating any of them is how a rail and a review card
 * come to disagree about whether the same section is finished.
 */

export type StepKey =
  | "details"
  | "context"
  | "reporting"
  | "compensation"
  | "assessment"
  | "review";

export interface PositionStep {
  key: StepKey;
  /** The short name in the rail and in "Next: …". */
  name: string;
  /** The step's own heading above the form. */
  heading: string;
  subheading: string;
  /** The rail's one-line reading of what this step currently holds. */
  summary: (position: Position) => string;
  detail: (position: Position) => string;
  isDone: (position: Position) => boolean;
}

const dash = (value: string | null | undefined) => (value?.trim() ? value : "—");

export const POSITION_STEPS: PositionStep[] = [
  {
    key: "details",
    name: "Position details",
    heading: "Position details",
    subheading:
      "Specify the primary role parameters. Attach the position description to keep it with the mandate.",
    summary: (p) => p.details.roleTitle.trim() || "Untitled role",
    detail: (p) => `${dash(p.details.department)} · ${dash(p.details.location)}`,
    isDone: (p) =>
      Boolean(p.details.roleTitle.trim() && p.details.department?.trim() && p.details.location?.trim()),
  },
  {
    key: "context",
    name: "Mandate context",
    heading: "Mandate context",
    subheading:
      "Why this mandate exists and the business drivers behind it. Internal only — never shown to candidates.",
    summary: (p) => MANDATE_REASON_LABELS[p.context.mandateReason],
    detail: (p) =>
      `${HIRING_URGENCY_LABELS[p.context.hiringUrgency]} · ${
        p.context.confidential ? "Confidential" : "Standard"
      }`,
    isDone: (p) => Boolean(p.context.businessDriver?.trim()),
  },
  {
    key: "reporting",
    name: "Reporting",
    heading: "Reporting structure",
    subheading: "Define who this role reports to and the team it will lead.",
    summary: (p) => `Reports to ${dash(p.reporting.reportsToName ?? p.reporting.reportsTo)}`,
    detail: (p) => {
      const seats = p.reporting.directReports.length;
      const seniority = labelOf(SENIORITY_LABELS, p.details.seniority);
      const start = p.reporting.targetStart ? formatDate(p.reporting.targetStart) : null;
      return [`${seats} direct report${seats === 1 ? "" : "s"}`, seniority, start]
        .filter(Boolean)
        .join(" · ");
    },
    isDone: (p) => Boolean(p.reporting.reportsTo?.trim()) && p.reporting.directReports.length > 0,
  },
  {
    key: "compensation",
    name: "Compensation",
    heading: "Compensation package",
    subheading: "Define base, bonus, long-term incentive and allowances for this role.",
    summary: (p) => {
      const total = packageTotal(p.compensation);
      if (total.min === null || total.max === null) return "Awaiting package input";
      return `${thousands(p.compensation.currency, total.min)} – ${thousands(
        p.compensation.currency,
        total.max,
      )}`;
    },
    detail: () => "Total target annual package",
    isDone: (p) => p.compensation.salaryMin !== null && p.compensation.salaryMax !== null,
  },
  {
    key: "assessment",
    name: "Assessment criteria",
    heading: "Assessment criteria",
    subheading:
      "Define the competencies and attributes candidates are scored against, and their relative weights.",
    summary: (p) =>
      `Technical ${panelTotal(p, "technical")}% · Behavioural ${panelTotal(p, "behavioural")}%`,
    detail: (p) => {
      const count = p.assessment.criteria.length;
      return `${count} screening criteri${count === 1 ? "on" : "a"}`;
    },
    isDone: (p) => panelTotal(p, "technical") === 100 && panelTotal(p, "behavioural") === 100,
  },
  {
    key: "review",
    name: "Review & publish",
    heading: "Review & publish",
    subheading: "Review every section before publishing the position profile.",
    summary: (p) => (p.publication.publishedAt ? "Profile published" : "Final validation"),
    detail: (p) =>
      p.publication.publishedAt
        ? `Published ${formatInstantDate(p.publication.publishedAt)}`
        : "Publish to record that the brief is ready",
    isDone: (p) => Boolean(p.publication.publishedAt),
  },
];

/** The steps a review card is drawn for — everything except the review step itself. */
export const REVIEWABLE_STEPS = POSITION_STEPS.slice(0, -1);

export function panelTotal(position: Position, panel: "technical" | "behavioural"): number {
  return position.assessment[panel].reduce((sum, competency) => sum + competency.weight, 0);
}

export function stepIndexOf(key: StepKey): number {
  return POSITION_STEPS.findIndex((step) => step.key === key);
}

/** How far through the brief the mandate is, as the mockup counts it: done steps out of six. */
export function completion(position: Position): number {
  const done = POSITION_STEPS.filter((step) => step.isDone(position)).length;
  return Math.round((done / POSITION_STEPS.length) * 100);
}

function thousands(currency: string, amount: number): string {
  return `${currency} ${Math.round(amount / 1000).toLocaleString()}K`;
}
