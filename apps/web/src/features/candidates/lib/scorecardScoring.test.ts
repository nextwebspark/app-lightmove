import { describe, expect, it } from "vitest";
import type { ScorecardCriterion, ScorecardPanel, ScorecardPanelTally } from "../api/scorecardTypes";
import { criterionKey, tallyOf } from "./scorecardScoring";

const criterion = (panel: ScorecardPanel, name: string, weight: number): ScorecardCriterion => ({
  panel,
  name,
  description: null,
  weight,
  weightInPanel: 0,
  maxPoints: 0,
  score: null,
  points: 0,
  evidence: null,
});

const panel = (name: ScorecardPanel, share: number, criteria: ScorecardCriterion[]): ScorecardPanelTally => ({
  panel: name,
  share,
  points: 0,
  average: null,
  scoredCount: 0,
  criteria,
});

// The server's ScorecardScoringTest uses the same brief and scores: the two must agree.
const MOCKUP = [
  panel("technical", 60, [
    criterion("technical", "Enterprise Architecture", 35),
    criterion("technical", "Cybersecurity", 35),
    criterion("technical", "IT Operations", 30),
  ]),
  panel("behavioural", 40, [
    criterion("behavioural", "Strategic Leadership", 30),
    criterion("behavioural", "Stakeholder Influence", 25),
    criterion("behavioural", "Change Management", 25),
    criterion("behavioural", "Resilience", 20),
  ]),
];

describe("tallyOf", () => {
  it("adds the mockup's example up as the server does", () => {
    const scores: Record<string, number> = {
      [criterionKey("technical", "Enterprise Architecture")]: 8,
      [criterionKey("technical", "Cybersecurity")]: 6,
      [criterionKey("technical", "IT Operations")]: 7,
      [criterionKey("behavioural", "Strategic Leadership")]: 8,
      [criterionKey("behavioural", "Stakeholder Influence")]: 6,
      [criterionKey("behavioural", "Change Management")]: 7,
      [criterionKey("behavioural", "Resilience")]: 7,
    };
    const tally = tallyOf(MOCKUP, (key) => scores[key] ?? null);

    expect(tally.panels.map((p) => p.points)).toEqual([42, 28.2]);
    expect(tally.overall).toBe(70.2);
    expect(tally.scoredCount).toBe(7);
  });

  it("counts an unscored criterion as unscored, earning nothing", () => {
    const tally = tallyOf(MOCKUP, (key) => (key === criterionKey("technical", "Cybersecurity") ? 0 : null));

    expect(tally.scoredCount).toBe(1);
    expect(tally.overall).toBe(0);
    expect(tally.panels[0].average).toBe(0);
    expect(tally.panels[1].average).toBeNull();
  });

  it("never loses a point to rounding when weights split three ways", () => {
    const thirds = [panel("technical", 100, ["A", "B", "C"].map((name) => criterion("technical", name, 1)))];

    expect(tallyOf(thirds, () => 10).overall).toBe(100);
  });
});
