import type { RankedCandidate, RankingCriterion } from "../api/types";
import {
  differentiators,
  focusCriterion,
  gapWithout,
  hasBothPanels,
  type LeaderFlip,
  type RankedRow,
  SENSITIVITY_FROM,
  SENSITIVITY_TO,
  type Standing,
  standingOf,
  type Weighting,
} from "./ranking";

/**
 * Every sentence the Ranking chapter says about its numbers, built from them alone — no model is asked, so each
 * claim is one the arithmetic makes. A panel's points read out of 10, as the scores do.
 */

export const oneDecimal = (value: number) => (Math.round(value * 10) / 10).toFixed(1);

export function signedPoints(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  if (rounded === 0) return "0.00 pts";
  return `${rounded > 0 ? "+" : "−"}${Math.abs(rounded).toFixed(2)} pts`;
}

export function rankShiftLabel(shift: number): string {
  if (shift > 0) return `Up ${shift}`;
  if (shift < 0) return `Down ${-shift}`;
  return "No change";
}

export const firstName = (candidate: RankedCandidate) => candidate.fullName.split(/\s+/)[0] || candidate.fullName;
const possessive = (candidate: RankedCandidate) => `${firstName(candidate)}'s`;
const outOfTen = (points: number) => `${oneDecimal(points / 10)}/10`;

export function joinNames(names: readonly string[]): string {
  if (names.length < 2) return names.join("");
  return `${names.slice(0, -1).join(", ")} and ${names[names.length - 1]}`;
}

export const splitLabel = (share: number) => `${share}:${100 - share}`;

export interface Insight {
  title: string;
  lead: string | null;
  text: string;
}

export function insightOf(
  rows: readonly RankedRow[],
  criteria: readonly RankingCriterion[],
  baseline: Weighting,
  scenario: Weighting,
  atBaseline: boolean,
): Insight | null {
  const [leader, runner] = rows;
  if (!leader) return null;
  const title = `${firstName(leader.candidate)} leads under these priorities`;
  if (!runner) {
    return { title, lead: null, text: `${firstName(leader.candidate)} is the only candidate assessed so far.` };
  }
  const lead = `${oneDecimal(leader.total - runner.total)}-point lead over ${firstName(runner.candidate)}`;
  if (atBaseline) {
    return {
      title,
      lead,
      text: `On the agreed ${splitLabel(baseline.share)} baseline, ${firstName(leader.candidate)} scores ${oneDecimal(leader.total)} against ${possessive(runner.candidate)} ${oneDecimal(runner.total)}.`,
    };
  }
  const leaderPoints = standingOf(leader.candidate, criteria, scenario).points;
  const runnerPoints = standingOf(runner.candidate, criteria, scenario).points;
  const technicalLeads = scenario.share >= 50;
  const panel = technicalLeads ? "technical" : "behavioural";
  const leaderPanel = technicalLeads ? leaderPoints.technical : leaderPoints.behavioural;
  const runnerPanel = technicalLeads ? runnerPoints.technical : runnerPoints.behavioural;
  const share = technicalLeads ? scenario.share : 100 - scenario.share;
  const verdict = leaderPanel >= runnerPanel ? "favours" : "does not overturn";
  const text = hasBothPanels(criteria)
    ? `${technicalLeads ? "Technical" : "Behavioural"} weighting at ${share}% ${verdict} ${possessive(leader.candidate)} ${outOfTen(leaderPanel)} ${panel} score.`
    : `${firstName(leader.candidate)} scores ${oneDecimal(leader.total)} against ${possessive(runner.candidate)} ${oneDecimal(runner.total)}.`;
  return { title, lead, text: text + focusLine(leader.candidate, runner.candidate, criteria, scenario) };
}

/** Names the scenario's furthest-moved competency, and whether giving it that weight helps `a`'s lead or costs it. */
function focusLine(
  a: RankedCandidate,
  b: RankedCandidate,
  criteria: readonly RankingCriterion[],
  scenario: Weighting,
): string {
  const focus = focusCriterion(criteria, scenario.weights);
  if (!focus) return "";
  const lead = standingOf(a, criteria, scenario).total - standingOf(b, criteria, scenario).total;
  const leadWithoutIt = gapWithout(focus, a, b, criteria, scenario);
  const direction = scenario.weights[focus.key] > focus.weight ? "Greater" : "Less";
  const effect = leadWithoutIt > lead ? "narrows" : "extends";
  return ` ${direction} emphasis on ${focus.name} within ${focus.panel} ${effect} ${possessive(a)} lead.`;
}

export interface OrderExplanation {
  title: string;
  baselineEyebrow: string;
  baselineText: string;
  scenarioEyebrow: string;
  scenarioText: string;
  text: string;
}

/** Why the selected pair sits as it does under the scenario, against where the baseline put them. */
export function whyOrderOf(
  pair: readonly [Standing, Standing],
  pairAtBaseline: readonly [Standing, Standing],
  criteria: readonly RankingCriterion[],
  baseline: Weighting,
  scenario: Weighting,
  atBaseline: boolean,
): OrderExplanation {
  const [a, b] = pair;
  const [baseA, baseB] = pairAtBaseline;
  const baselineLeader = baseA.total >= baseB.total ? baseA : baseB;
  const baselineGap = Math.abs(baseA.total - baseB.total);
  const changed = criteria.some((criterion) => scenario.weights[criterion.key] !== criterion.weight);
  const scenarioEyebrow = atBaseline
    ? `AGREED BASELINE · ${splitLabel(baseline.share)}`
    : `${splitLabel(scenario.share)}${changed ? " + COMPETENCY WEIGHT CHANGES" : ""}`;
  const allocation = hasBothPanels(criteria)
    ? `The ${scenario.share}% technical allocation ${a.points.technical >= b.points.technical ? "favours" : "limits"} ${possessive(a.candidate)} ${outOfTen(a.points.technical)} technical score.`
    : "Only one group of competencies is scored, so the technical split moves nothing.";
  return {
    title: baselineLeader.candidate.candidateId === a.candidate.candidateId ? "Why the order holds" : "Why the order changes",
    baselineEyebrow: `AGREED BASELINE · ${splitLabel(baseline.share)}`,
    baselineText: `${firstName(baselineLeader.candidate)} leads by ${oneDecimal(baselineGap)} pts`,
    scenarioEyebrow,
    scenarioText: `${firstName(a.candidate)} leads by ${oneDecimal(a.total - b.total)} pts`,
    text: `${allocation}${focusLine(a.candidate, b.candidate, criteria, scenario)} Assessed scores are unchanged.`,
  };
}

export function sensitivityOf(flips: readonly LeaderFlip[] | null, leader: RankedCandidate | undefined): string | null {
  if (flips === null || !leader) return null;
  if (flips.length === 0) {
    return `Reference sensitivity: ${firstName(leader)} leads at every technical weighting from ${SENSITIVITY_FROM}% to ${SENSITIVITY_TO}%.`;
  }
  const points = flips.map(
    (flip, index) =>
      `${oneDecimal(flip.at)}%${index === 0 ? " technical" : ""} (${firstName(flip.from)} → ${firstName(flip.to)})`,
  );
  return `Reference sensitivity: ${points.join(", then ")}. Check these thresholds against the competency weights in force before relying on them.`;
}

export function differentiatorsOf(a: RankedCandidate, b: RankedCandidate, criteria: readonly RankingCriterion[]): string {
  const { forA, forB, notCompared } = differentiators(a, b, criteria);
  const side = (candidate: RankedCandidate, gaps: typeof forA) =>
    gaps.length === 0
      ? null
      : `${firstName(candidate)}: ${joinNames(gaps.map((gap) => `+${oneDecimal(gap.gap)} in ${gap.criterion.name}`))}.`;
  const sides = [side(a, forA), side(b, forB)].filter(Boolean);
  const lines = sides.length > 0 ? sides.join(" ") : "They score alike on every competency both have scored.";
  return notCompared > 0
    ? `${lines} ${notCompared} ${notCompared === 1 ? "competency is" : "competencies are"} not compared, being unscored on one side.`
    : lines;
}

export function validateOf(pair: readonly [RankedRow, RankedRow], baselineLeader: RankedCandidate): string {
  const unscored = pair[0].unscoredCount + pair[1].unscoredCount;
  const open =
    unscored > 0
      ? `Score the ${unscored} unscored ${unscored === 1 ? "competency" : "competencies"} before relying on this order. `
      : "";
  return `${open}Review the evidence behind the largest competency differences, and confirm which assessed competencies support ${possessive(baselineLeader)} baseline advantage before deciding.`;
}

export function evidenceContextOf(candidate: RankedCandidate, formatDate: (iso: string) => string): string {
  const { assessment } = candidate;
  const reviewed =
    assessment.status === "reviewed"
      ? `reviewed${assessment.reviewedByName ? ` by ${assessment.reviewedByName}` : ""}${assessment.reviewedAt ? ` on ${formatDate(assessment.reviewedAt)}` : ""}`
      : "a draft, not yet reviewed";
  const evidenced = assessment.scores.filter((score) => score.hasEvidence).length;
  const parts = [
    `${firstName(candidate)}: ${reviewed}`,
    `evidence on ${evidenced} of ${assessment.criteriaCount}`,
  ];
  const unscored = assessment.criteriaCount - assessment.scoredCount;
  if (unscored > 0) parts.push(`${unscored} unscored`);
  if (assessment.retiredCount > 0) {
    parts.push(`${assessment.retiredCount} retired ${assessment.retiredCount === 1 ? "score" : "scores"} not counted`);
  }
  return `${parts.join(" · ")}.`;
}
