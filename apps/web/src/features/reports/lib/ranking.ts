import type { CompetencyPanel, RankedCandidate, Ranking, RankingCriterion } from "../api/types";

/**
 * The Ranking chapter's arithmetic, the same as the drawer's Assessment tab (`ScorecardScoring` on the server,
 * `candidates/lib/scorecardScoring.ts` here): each panel carries its share, each competency its weight's part of its
 * panel, an unscored one earns nothing. Kept unrounded — a shift between two weightings is read off the raw totals.
 */

export type Weights = Record<string, number>;

/** A technical share and a weight per competency: the brief's own (the baseline) or a scenario's. */
export interface Weighting {
  share: number;
  weights: Weights;
}

/** Each panel's points out of 100, as though that panel carried the whole assessment. */
export interface PanelPoints {
  technical: number;
  behavioural: number;
}

export interface Standing {
  candidate: RankedCandidate;
  points: PanelPoints;
  /** Out of 100 at the weighting's share. */
  total: number;
}

export interface RankedRow {
  candidate: RankedCandidate;
  rank: number;
  baselineRank: number;
  total: number;
  baselineTotal: number;
  /** Places moved up from the baseline: positive is up. */
  rankShift: number;
  scoreShift: number;
  unscoredCount: number;
}

export interface LeaderFlip {
  /** The technical share at which `to` overtakes `from`. */
  at: number;
  from: RankedCandidate;
  to: RankedCandidate;
}

export interface Differentiator {
  criterion: RankingCriterion;
  gap: number;
}

export const PANELS: readonly CompetencyPanel[] = ["technical", "behavioural"];
export const SENSITIVITY_FROM = 30;
export const SENSITIVITY_TO = 80;
const EPSILON = 1e-9;

export function baselineOf(ranking: Pick<Ranking, "criteria" | "baselineTechnicalShare">): Weighting {
  return {
    share: ranking.baselineTechnicalShare,
    weights: Object.fromEntries(ranking.criteria.map((criterion) => [criterion.key, criterion.weight])),
  };
}

export function criteriaOf(criteria: readonly RankingCriterion[], panel: CompetencyPanel): RankingCriterion[] {
  return criteria.filter((criterion) => criterion.panel === panel);
}

/** A panel with no competencies gives its share to the other, so a brief scoring one panel still reads out of 100. */
export function effectiveShare(criteria: readonly RankingCriterion[], share: number): number {
  const hasTechnical = criteria.some((criterion) => criterion.panel === "technical");
  const hasBehavioural = criteria.some((criterion) => criterion.panel === "behavioural");
  if (!hasTechnical) return 0;
  if (!hasBehavioural) return 100;
  return Math.min(100, Math.max(0, share));
}

export function hasBothPanels(criteria: readonly RankingCriterion[]): boolean {
  return PANELS.every((panel) => criteria.some((criterion) => criterion.panel === panel));
}

/** A competency's part of its panel, out of 100; a panel weighing nothing splits evenly. */
export function weightInPanel(criterion: RankingCriterion, criteria: readonly RankingCriterion[], weights: Weights) {
  const panel = criteriaOf(criteria, criterion.panel);
  const sum = panel.reduce((total, each) => total + (weights[each.key] ?? 0), 0);
  return sum === 0 ? 100 / panel.length : (100 * (weights[criterion.key] ?? 0)) / sum;
}

/** What a competency carries of the whole 100 at this weighting — the "Effective overall" line. */
export function effectiveOverall(criterion: RankingCriterion, criteria: readonly RankingCriterion[], weighting: Weighting) {
  const technicalPart = effectiveShare(criteria, weighting.share);
  const panelPart = criterion.panel === "technical" ? technicalPart : 100 - technicalPart;
  return (panelPart * weightInPanel(criterion, criteria, weighting.weights)) / 100;
}

export function scoreOf(candidate: RankedCandidate, key: string): number | null {
  return candidate.assessment.scores.find((score) => score.key === key)?.score ?? null;
}

export function panelPointsOf(
  candidate: RankedCandidate,
  criteria: readonly RankingCriterion[],
  weights: Weights,
): PanelPoints {
  const pointsOf = (panel: CompetencyPanel) =>
    criteriaOf(criteria, panel).reduce((points, criterion) => {
      const score = scoreOf(candidate, criterion.key);
      return score === null ? points : points + (score / 10) * weightInPanel(criterion, criteria, weights);
    }, 0);
  return { technical: pointsOf("technical"), behavioural: pointsOf("behavioural") };
}

export function totalAt(points: PanelPoints, technicalPart: number): number {
  return (technicalPart / 100) * points.technical + (1 - technicalPart / 100) * points.behavioural;
}

export function standingOf(
  candidate: RankedCandidate,
  criteria: readonly RankingCriterion[],
  weighting: Weighting,
): Standing {
  const points = panelPointsOf(candidate, criteria, weighting.weights);
  return { candidate, points, total: totalAt(points, effectiveShare(criteria, weighting.share)) };
}

/** Best first: the higher total, then a reviewed assessment over a draft, the more scored, the name, the id. */
export function compareStandings(a: Standing, b: Standing): number {
  if (Math.abs(a.total - b.total) > EPSILON) return b.total - a.total;
  const reviewed = Number(b.candidate.assessment.status === "reviewed") - Number(a.candidate.assessment.status === "reviewed");
  if (reviewed !== 0) return reviewed;
  const scored = b.candidate.assessment.scoredCount - a.candidate.assessment.scoredCount;
  if (scored !== 0) return scored;
  const byName = a.candidate.fullName.localeCompare(b.candidate.fullName);
  if (byName !== 0) return byName;
  return a.candidate.candidateId.localeCompare(b.candidate.candidateId);
}

export function rankStandings(
  candidates: readonly RankedCandidate[],
  criteria: readonly RankingCriterion[],
  weighting: Weighting,
): Standing[] {
  return candidates.map((candidate) => standingOf(candidate, criteria, weighting)).sort(compareStandings);
}

/** The pool ranked under the scenario, each row beside where the baseline put it. */
export function rankRows(
  candidates: readonly RankedCandidate[],
  criteria: readonly RankingCriterion[],
  baseline: Weighting,
  scenario: Weighting,
): RankedRow[] {
  const atBaseline = rankStandings(candidates, criteria, baseline);
  const baselineOf = new Map(atBaseline.map((standing, index) => [standing.candidate.candidateId, { standing, index }]));
  return rankStandings(candidates, criteria, scenario).map((standing, index) => {
    const before = baselineOf.get(standing.candidate.candidateId)!;
    return {
      candidate: standing.candidate,
      rank: index + 1,
      baselineRank: before.index + 1,
      total: standing.total,
      baselineTotal: before.standing.total,
      rankShift: before.index - index,
      scoreShift: standing.total - before.standing.total,
      unscoredCount: standing.candidate.assessment.criteriaCount - standing.candidate.assessment.scoredCount,
    };
  });
}

export function changedCriteria(criteria: readonly RankingCriterion[], weights: Weights): RankingCriterion[] {
  return criteria.filter((criterion) => (weights[criterion.key] ?? criterion.weight) !== criterion.weight);
}

export function isBaseline(criteria: readonly RankingCriterion[], baseline: Weighting, weighting: Weighting): boolean {
  return weighting.share === baseline.share && changedCriteria(criteria, weighting.weights).length === 0;
}

export function panelWeightTotal(criteria: readonly RankingCriterion[], panel: CompetencyPanel, weights: Weights) {
  return criteriaOf(criteria, panel).reduce((total, criterion) => total + (weights[criterion.key] ?? 0), 0);
}

/** The panels someone changed without bringing them to 100 — what holds a save, as the server holds it. */
export function panelsOffHundred(criteria: readonly RankingCriterion[], weights: Weights): CompetencyPanel[] {
  return PANELS.filter((panel) => {
    const changed = criteriaOf(criteria, panel).some((criterion) => weights[criterion.key] !== criterion.weight);
    return changed && panelWeightTotal(criteria, panel, weights) !== 100;
  });
}

/** The changed competency that moved furthest from the brief: the one the screen names as the scenario's lever. */
export function focusCriterion(criteria: readonly RankingCriterion[], weights: Weights): RankingCriterion | null {
  const changed = changedCriteria(criteria, weights);
  if (changed.length === 0) return null;
  return changed.reduce((furthest, criterion) =>
    Math.abs(weights[criterion.key] - criterion.weight) > Math.abs(weights[furthest.key] - furthest.weight)
      ? criterion
      : furthest,
  );
}

/** `a`'s lead over `b` at this weighting with one competency put back to the brief's weight. */
export function gapWithout(
  focus: RankingCriterion,
  a: RankedCandidate,
  b: RankedCandidate,
  criteria: readonly RankingCriterion[],
  weighting: Weighting,
): number {
  const reverted = { ...weighting, weights: { ...weighting.weights, [focus.key]: focus.weight } };
  return standingOf(a, criteria, reverted).total - standingOf(b, criteria, reverted).total;
}

/**
 * Where a pair's competency scores differ most, each side's own strengths first: a gap of a point or more, else the
 * largest there is. Only competencies both have scored are compared — an unscored one is an open question.
 */
export function differentiators(
  a: RankedCandidate,
  b: RankedCandidate,
  criteria: readonly RankingCriterion[],
): { forA: Differentiator[]; forB: Differentiator[]; notCompared: number } {
  const compared: Differentiator[] = [];
  let notCompared = 0;
  for (const criterion of criteria) {
    const scoreA = scoreOf(a, criterion.key);
    const scoreB = scoreOf(b, criterion.key);
    if (scoreA === null || scoreB === null) {
      notCompared += 1;
    } else {
      compared.push({ criterion, gap: scoreA - scoreB });
    }
  }
  const strongest = (sign: 1 | -1) => {
    const favouring = compared
      .filter((each) => sign * each.gap > 0)
      .sort((left, right) => sign * right.gap - sign * left.gap)
      .map((each) => ({ criterion: each.criterion, gap: Math.abs(each.gap) }));
    const wide = favouring.filter((each) => each.gap >= 1);
    return (wide.length > 0 ? wide : favouring).slice(0, 2);
  };
  return { forA: strongest(1), forB: strongest(-1), notCompared };
}

const slopeOf = (points: PanelPoints) => (points.technical - points.behavioural) / 100;

/**
 * Every technical share between `from` and `to` at which the leader changes. A total is a line in the share
 * (`behavioural + share × slope`), so each overtaking is found exactly rather than by stepping a slider: from the
 * leader at `from`, the next one is the steeper line that crosses it soonest. Null when a panel is empty, since the
 * share then moves nothing.
 */
export function leaderFlips(
  standings: readonly Standing[],
  criteria: readonly RankingCriterion[],
  from = SENSITIVITY_FROM,
  to = SENSITIVITY_TO,
): LeaderFlip[] | null {
  if (!hasBothPanels(criteria)) return null;
  if (standings.length < 2) return [];
  const atShare = (standing: Standing, share: number): Standing => ({ ...standing, total: totalAt(standing.points, share) });
  let leader = [...standings].map((standing) => atShare(standing, from)).sort(compareStandings)[0];
  let share = from;
  const flips: LeaderFlip[] = [];
  for (;;) {
    const leaderSlope = slopeOf(leader.points);
    let next: { standing: Standing; at: number } | null = null;
    for (const challenger of standings) {
      const slope = slopeOf(challenger.points);
      if (challenger.candidate.candidateId === leader.candidate.candidateId || slope <= leaderSlope + EPSILON) continue;
      const at = (leader.points.behavioural - challenger.points.behavioural) / (slope - leaderSlope);
      if (at <= share + EPSILON || at > to + EPSILON) continue;
      if (!next || at < next.at - EPSILON || (Math.abs(at - next.at) <= EPSILON && slope > slopeOf(next.standing.points))) {
        next = { standing: challenger, at };
      }
    }
    if (!next) return flips;
    flips.push({ at: next.at, from: leader.candidate, to: next.standing.candidate });
    leader = next.standing;
    share = next.at;
  }
}

/** The technical share at which two candidates' totals meet, or null where they never do between 0 and 100. */
export function pairCrossover(a: Standing, b: Standing): number | null {
  const slopeA = slopeOf(a.points);
  const slopeB = slopeOf(b.points);
  if (Math.abs(slopeA - slopeB) <= EPSILON) return null;
  const at = (b.points.behavioural - a.points.behavioural) / (slopeA - slopeB);
  return at >= 0 && at <= 100 ? at : null;
}
