import { ICONS } from "../../../components/layout/Icon";
import { formatInstantDate } from "../../../lib/format";
import type { Position } from "../api/types";
import { packageTotal } from "./compensation";
import { directReportsOf, labelOfNode, managerOf } from "./orgChart";
import { SENIORITY_LABELS, labelOf } from "./labels";

/**
 * The five screens of the brief, in one array.
 *
 * Everything that knows about steps reads from here — the rail, the review cards, the readiness bar
 * and the step in the URL — so a step's name, its lede, the rule for calling it done and the words
 * for why it is not are each stated exactly once. Duplicating any of them is how a rail and a review
 * card come to disagree about whether the same section is finished.
 */

export type StepKey = "brief" | "reporting" | "compensation" | "assessment" | "review";

/** The query parameter the step lives in — `?step=compensation` — so a step can be linked to. */
export const STEP_PARAM = "step";

export interface PositionStep {
  key: StepKey;
  /** The short name in the rail. */
  name: string;
  /** The step's own heading above the form. */
  heading: string;
  lede: string;
  icon: string;
  /** The rail's one-line reading of what this step currently holds. */
  summary: (position: Position) => string;
  isDone: (position: Position) => boolean;
  /** Why the step is not done, in the words its review card wears — null once it is. */
  attention: (position: Position) => string | null;
}

export const POSITION_STEPS: PositionStep[] = [
  {
    key: "brief",
    name: "Role Brief",
    heading: "Role Brief",
    lede: "Define the role and why it exists. Attach the position description to keep it with the mandate.",
    icon: ICONS.file,
    summary: (p) => `${p.details.roleTitle.trim() || "Untitled role"} · ${placeOf(p) ?? "No location"}`,
    isDone: (p) => Boolean(p.details.roleTitle.trim() && placeOf(p)),
    attention: (p) => {
      if (!p.details.roleTitle.trim()) return "The role has no title yet.";
      if (!placeOf(p)) return "No location yet — name the city or the country.";
      return null;
    },
  },
  {
    key: "reporting",
    name: "Reporting",
    heading: "Reporting Structure",
    lede: "Define who this role reports to and the team it will lead.",
    icon: ICONS.orgChart,
    summary: (p) =>
      [labelOfNode(managerOf(p.reporting.orgChart)) ?? "No manager", labelOf(SENIORITY_LABELS, p.details.seniority)]
        .filter(Boolean)
        .join(" · "),
    isDone: (p) =>
      Boolean(labelOfNode(managerOf(p.reporting.orgChart))) && directReportsOf(p.reporting.orgChart).length > 0,
    attention: (p) => {
      if (!labelOfNode(managerOf(p.reporting.orgChart))) return "Nobody is named as the manager yet.";
      if (directReportsOf(p.reporting.orgChart).length === 0) return "No direct reports drawn yet.";
      return null;
    },
  },
  {
    key: "compensation",
    name: "Compensation",
    heading: "Compensation Package",
    lede: "Define base, bonus, long-term incentive and allowances for this role.",
    icon: ICONS.currency,
    summary: (p) => {
      const total = packageTotal(p.compensation);
      if (total.min === null || total.max === null) return "Awaiting package input";
      return `${p.compensation.currency} ${thousands(total.min)} – ${thousands(total.max)}`;
    },
    isDone: (p) => hasBand(p) && allowancesQuantified(p),
    attention: (p) => {
      if (!hasBand(p)) return "No base salary band yet.";
      if (!allowancesQuantified(p)) return "Allowances not fully quantified — give every benefit a figure.";
      return null;
    },
  },
  {
    key: "assessment",
    name: "Assessment Criteria",
    heading: "Assessment Criteria",
    lede: "Define the screening gates and competency weights used to evaluate candidates.",
    icon: ICONS.clipboard,
    summary: (p) =>
      `Technical ${p.assessment.technicalShare}% · Behavioural ${100 - p.assessment.technicalShare}%`,
    isDone: (p) => panelTotal(p, "technical") === 100 && panelTotal(p, "behavioural") === 100,
    attention: (p) => {
      const technical = panelTotal(p, "technical");
      if (technical !== 100) return `Technical weights total ${technical}%, not 100%.`;
      const behavioural = panelTotal(p, "behavioural");
      if (behavioural !== 100) return `Behavioural weights total ${behavioural}%, not 100%.`;
      return null;
    },
  },
  {
    key: "review",
    name: "Review & Publish",
    heading: "Review & publish",
    lede: "Review every section before publishing the position profile.",
    icon: ICONS.rocket,
    summary: (p) =>
      p.publication.publishedAt ? `Published ${formatInstantDate(p.publication.publishedAt)}` : "Not yet published",
    isDone: (p) => Boolean(p.publication.publishedAt),
    attention: () => null,
  },
];

/** The steps a review card is drawn for — everything except the review step itself. */
export const REVIEWABLE_STEPS = POSITION_STEPS.slice(0, -1);

export function stepIndexOf(key: StepKey): number {
  return POSITION_STEPS.findIndex((step) => step.key === key);
}

/**
 * Where the brief opens when the URL names no step: a published brief opens on its own review —
 * somebody coming back to a finished mandate is looking at what was published rather than starting
 * the brief again — and anything else opens on the Role Brief. Decided once, when the screen mounts:
 * publishing from the Role Brief must not swap the screen underneath the person who pressed the button.
 */
export function openingStepOf(position: Position): StepKey {
  return position.publication.publishedAt ? "review" : "brief";
}

/** The step a URL asks for, or the one the screen opened on when it names none or nonsense. */
export function stepOf(requested: string | null, fallback: StepKey): PositionStep {
  return POSITION_STEPS.find((step) => step.key === requested) ?? POSITION_STEPS[stepIndexOf(fallback)];
}

export function panelTotal(position: Position, panel: "technical" | "behavioural"): number {
  return position.assessment[panel].reduce((sum, competency) => sum + competency.weight, 0);
}

/** One flag per step, in rail order. */
export function doneSteps(position: Position): boolean[] {
  return POSITION_STEPS.map((step) => step.isDone(position));
}

/** How far through the brief the mandate is: done steps out of five, as a percentage. */
export function completion(position: Position): number {
  const done = doneSteps(position).filter(Boolean).length;
  return Math.round((done / POSITION_STEPS.length) * 100);
}

/**
 * The publication readiness checks the review lists. They report; they gate nothing — V38 retired
 * the readiness gate along with the lock, so publishing is available whatever they say and an
 * unfinished brief can still be declared ready by someone who means it.
 */
export function readinessOf(position: Position): { label: string; met: boolean }[] {
  return [
    {
      label: "Role parameters, mandate context and reporting lines specified",
      met: Boolean(
        position.details.roleTitle.trim() && placeOf(position) && labelOfNode(managerOf(position.reporting.orgChart)),
      ),
    },
    {
      label: "Compensation package captured with allowances fully quantified",
      met: hasBand(position) && allowancesQuantified(position),
    },
    {
      label: "Technical and behavioural weighting totals exactly 100%",
      met: panelTotal(position, "technical") === 100 && panelTotal(position, "behavioural") === 100,
    },
  ];
}

/** The brief's place as one line — "Riyadh, Saudi Arabia" — or null when neither half is named. */
export function placeLineOf(position: Position): string | null {
  const parts = [position.details.locationCity, position.details.locationCountry]
    .map((half) => half?.trim())
    .filter(Boolean);
  return parts.length > 0 ? parts.join(", ") : null;
}

/** The shorter reading the rail uses: the city where one is named, else the country. */
function placeOf(position: Position): string | null {
  return position.details.locationCity?.trim() || position.details.locationCountry?.trim() || null;
}

function hasBand(position: Position): boolean {
  return position.compensation.salaryMin !== null && position.compensation.salaryMax !== null;
}

function allowancesQuantified(position: Position): boolean {
  return position.compensation.benefits.every((benefit) => benefit.amount !== null);
}

function thousands(amount: number): string {
  return `${Math.round(amount / 1000).toLocaleString()}K`;
}
