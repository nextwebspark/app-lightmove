import type { ScorecardCriterion, ScorecardPanel, ScorecardPanelTally } from "../api/scorecardTypes";

/**
 * The server's `ScorecardScoring`, drawn while a stepper moves: each panel carries its share of the 100, each
 * criterion its weight's part of its panel, a 10 earns all of it, and an unscored criterion earns nothing.
 * Worked from the integer weights and share rather than the read's rounded points, so the live total and the
 * saved one never differ by a rounding.
 */
export interface LiveTally {
  overall: number;
  scoredCount: number;
  criteriaCount: number;
  panels: LivePanel[];
}

export interface LivePanel {
  panel: ScorecardPanel;
  share: number;
  points: number;
  average: number | null;
  scoredCount: number;
  criteria: (ScorecardCriterion & { score: number | null; points: number })[];
}

export function criterionKey(panel: ScorecardPanel, name: string): string {
  return `${panel}:${name.trim().toLowerCase()}`;
}

export function tallyOf(panels: readonly ScorecardPanelTally[], scoreOf: (key: string) => number | null): LiveTally {
  let overall = 0;
  let scoredCount = 0;
  let criteriaCount = 0;
  const live = panels.map((panel) => {
    let points = 0;
    let sum = 0;
    let scored = 0;
    const weightSum = panel.criteria.reduce((total, criterion) => total + criterion.weight, 0);
    const count = panel.criteria.length;
    const criteria = panel.criteria.map((criterion) => {
      const score = scoreOf(criterionKey(criterion.panel, criterion.name));
      const weightInPanel = partOfPanel(criterion.weight, weightSum, count);
      const maxPoints = (weightInPanel / 100) * panel.share;
      const earned = score === null ? 0 : (score / 10) * maxPoints;
      if (score !== null) {
        sum += score;
        scored += 1;
      }
      points += earned;
      return { ...criterion, score, points: roundOne(earned) };
    });
    overall += points;
    scoredCount += scored;
    criteriaCount += criteria.length;
    return {
      panel: panel.panel,
      share: panel.share,
      points: roundOne(points),
      average: scored === 0 ? null : roundOne(sum / scored),
      scoredCount: scored,
      criteria,
    };
  });
  return { overall: roundOne(overall), scoredCount, criteriaCount, panels: live };
}

/**
 * A criterion's part of its panel, out of 100: its weight over the panel's, split evenly when the panel weighs
 * nothing. The one copy the drawer and the Ranking chapter share, so their totals cannot drift apart.
 */
export function partOfPanel(weight: number, panelWeight: number, panelSize: number): number {
  return panelWeight === 0 ? 100 / panelSize : (100 * weight) / panelWeight;
}

export function roundOne(value: number): number {
  return Math.round(value * 10) / 10;
}
