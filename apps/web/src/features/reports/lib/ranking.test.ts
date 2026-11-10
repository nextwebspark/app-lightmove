import { describe, expect, it } from "vitest";
import type { RankedCandidate, RankingCriterion } from "../api/types";
import {
  baselineOf,
  differentiators,
  effectiveOverall,
  leaderAt,
  leaderFlips,
  panelsOffHundred,
  rankRows,
  rankStandings,
  standingOf,
} from "./ranking";

const TREASURY: RankingCriterion = { key: "TECHNICAL:treasury", panel: "technical", name: "Treasury", description: null, weight: 60 };
const REPORTING: RankingCriterion = { key: "TECHNICAL:reporting", panel: "technical", name: "Reporting", description: null, weight: 40 };
const LEADERSHIP: RankingCriterion = { key: "BEHAVIOURAL:leadership", panel: "behavioural", name: "Leadership", description: null, weight: 100 };
const CRITERIA = [TREASURY, REPORTING, LEADERSHIP];
const BASELINE = baselineOf({ criteria: CRITERIA, baselineTechnicalShare: 60 });

function candidate(
  id: string,
  scores: Record<string, number | null>,
  status: "draft" | "reviewed" = "reviewed",
): RankedCandidate {
  const scored = Object.values(scores).filter((score) => score !== null).length;
  return {
    candidateId: id,
    personId: `person-${id}`,
    fullName: `${id[0].toUpperCase()}${id.slice(1)} Example`,
    title: null,
    companyName: null,
    companyLogoUrl: null,
    enrichedAt: null,
    statusLabel: null,
    statusColour: null,
    stageLabel: null,
    outOfProcess: false,
    processingRestricted: false,
    assessment: {
      status,
      updatedAt: null,
      reviewedAt: null,
      reviewedByName: null,
      baselineOverall: 0,
      scoredCount: scored,
      criteriaCount: CRITERIA.length,
      retiredCount: 0,
      scores: CRITERIA.map((criterion) => ({ key: criterion.key, score: scores[criterion.key] ?? null, hasEvidence: false })),
    },
  };
}

const scoresOf = (treasury: number | null, reporting: number | null, leadership: number | null) => ({
  [TREASURY.key]: treasury,
  [REPORTING.key]: reporting,
  [LEADERSHIP.key]: leadership,
});

describe("ranking arithmetic", () => {
  it("totals as the drawer does: each panel its share, each competency its part of its panel, unscored earning nothing", () => {
    // Treasury 10 and Leadership 5 on a 60:40 brief: 60 × 0.6 + 40 × 0.5 = 56, the scorecard test's figure.
    expect(standingOf(candidate("fatima", scoresOf(10, null, 5)), CRITERIA, BASELINE).total).toBeCloseTo(56);
  });

  it("gives an empty panel's share to the other, and splits a panel weighing nothing evenly", () => {
    const technicalOnly = [TREASURY, REPORTING];
    const unweighted = { share: 30, weights: { [TREASURY.key]: 0, [REPORTING.key]: 0 } };

    expect(standingOf(candidate("khalid", scoresOf(8, 6, null)), technicalOnly, unweighted).total).toBeCloseTo(70);
    expect(effectiveOverall(TREASURY, technicalOnly, unweighted)).toBeCloseTo(50);
  });

  it("orders equal totals reviewed first, then by more scored, then by name", () => {
    const draft = candidate("aisha", scoresOf(5, 5, 5), "draft");
    const reviewed = candidate("zayed", scoresOf(5, 5, 5));
    const fewerScored = candidate("bader", scoresOf(null, 10, 6.5));

    expect(standingOf(fewerScored, CRITERIA, BASELINE).total).toBeCloseTo(50);
    expect(rankStandings([fewerScored, draft, reviewed], CRITERIA, BASELINE).map((each) => each.candidate.candidateId))
      .toEqual(["zayed", "bader", "aisha"]);
    expect(rankStandings([candidate("omar", scoresOf(5, 5, 5)), reviewed], CRITERIA, BASELINE)[0].candidate.candidateId)
      .toBe("omar");
  });

  it("states each row's move from the baseline in places and in unrounded points", () => {
    const technical = candidate("tariq", scoresOf(10, 10, 4));
    const behavioural = candidate("layla", scoresOf(5, 5, 10));
    const scenario = { ...BASELINE, share: 30 };

    const [first, second] = rankRows([technical, behavioural], CRITERIA, BASELINE, scenario);

    expect(first.candidate.candidateId).toBe("layla");
    expect(first.rankShift).toBe(1);
    expect(second.rankShift).toBe(-1);
    expect(first.scoreShift).toBeCloseTo(85 - 70);
    expect(second.scoreShift).toBeCloseTo(58 - 76);
  });

  it("holds a save only for a group someone changed without bringing it to 100", () => {
    expect(panelsOffHundred(CRITERIA, { ...BASELINE.weights, [LEADERSHIP.key]: 100 })).toEqual([]);
    expect(panelsOffHundred(CRITERIA, { ...BASELINE.weights, [TREASURY.key]: 50 })).toEqual(["technical"]);
    const offBrief = [{ ...TREASURY, weight: 30 }, REPORTING, LEADERSHIP];
    expect(panelsOffHundred(offBrief, baselineOf({ criteria: offBrief, baselineTechnicalShare: 50 }).weights)).toEqual([]);
  });
});

describe("leader flips", () => {
  it("finds the exact share at which a steeper line overtakes the leader", () => {
    // Technical 100 / behavioural 40 against technical 40 / behavioural 90: equal at 50/1.1 ≈ 45.45% technical.
    const technical = candidate("tariq", scoresOf(10, 10, 4));
    const behavioural = candidate("layla", scoresOf(4, 4, 9));
    const standings = rankStandings([technical, behavioural], CRITERIA, BASELINE);

    const flips = leaderFlips(standings, CRITERIA)!;

    expect(flips).toHaveLength(1);
    expect(flips[0].at).toBeCloseTo(45.4545, 3);
    expect(flips[0].from.candidateId).toBe("layla");
    expect(flips[0].to.candidateId).toBe("tariq");
  });

  it("follows the lead through a chain of three, and finds none where one leads throughout", () => {
    const behavioural = candidate("layla", scoresOf(2, 2, 10));
    const balanced = candidate("omar", scoresOf(6, 6, 7));
    const technical = candidate("tariq", scoresOf(10, 10, 2));
    const chain = leaderFlips(rankStandings([behavioural, balanced, technical], CRITERIA, BASELINE), CRITERIA, 0, 100)!;

    expect(chain.map((flip) => [flip.from.candidateId, flip.to.candidateId])).toEqual([
      ["layla", "omar"],
      ["omar", "tariq"],
    ]);
    expect(chain[0].at).toBeLessThan(chain[1].at);

    const dominant = candidate("noura", scoresOf(9, 9, 9));
    expect(leaderFlips(rankStandings([dominant, balanced], CRITERIA, BASELINE), CRITERIA)).toEqual([]);
  });

  it("hands a tie where the range starts to the steeper line, so its overtaking is not lost", () => {
    // Both total 60 at 30% technical; Aisha's line is steeper, so she leads from just past it.
    const flat = candidate("zaid", scoresOf(6, 6, 6));
    const steep = candidate("aisha", scoresOf(8, 8, 360 / 70 / 1), "draft");
    const standings = rankStandings([flat, steep], CRITERIA, BASELINE);

    expect(leaderAt(standings, 30)?.candidate.candidateId).toBe("aisha");
    expect(leaderFlips(standings, CRITERIA)).toEqual([]);
  });

  it("says nothing about the split where only one panel is scored", () => {
    const technicalOnly = [TREASURY, REPORTING];
    const standings = rankStandings(
      [candidate("tariq", scoresOf(10, 10, null)), candidate("omar", scoresOf(6, 6, null))],
      technicalOnly,
      baselineOf({ criteria: technicalOnly, baselineTechnicalShare: 50 }),
    );
    expect(leaderFlips(standings, technicalOnly)).toBeNull();
  });
});

describe("differentiators", () => {
  it("names each side's widest gaps of a point or more, and leaves unscored competencies uncompared", () => {
    const a = candidate("tariq", scoresOf(9, 8, null));
    const b = candidate("layla", scoresOf(7, 7.5, 9));

    const { forA, forB, notCompared } = differentiators(a, b, CRITERIA);

    expect(forA.map((each) => [each.criterion.name, each.gap])).toEqual([["Treasury", 2]]);
    expect(forB).toEqual([]);
    expect(notCompared).toBe(1);
  });
});
