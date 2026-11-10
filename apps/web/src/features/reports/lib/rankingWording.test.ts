import { describe, expect, it } from "vitest";
import type { RankedCandidate, RankingCriterion } from "../api/types";
import { baselineOf, leaderAt, leaderFlips, rankRows, rankStandings, SENSITIVITY_FROM } from "./ranking";
import {
  differentiatorsOf,
  evidenceContextOf,
  insightOf,
  rankShiftLabel,
  sensitivityOf,
  signedPoints,
  validateOf,
} from "./rankingWording";

const TREASURY: RankingCriterion = { key: "TECHNICAL:treasury", panel: "technical", name: "Treasury", description: null, weight: 100 };
const LEADERSHIP: RankingCriterion = { key: "BEHAVIOURAL:leadership", panel: "behavioural", name: "Leadership", description: null, weight: 100 };
const CRITERIA = [TREASURY, LEADERSHIP];
const BASELINE = baselineOf({ criteria: CRITERIA, baselineTechnicalShare: 50 });

function candidate(id: string, name: string, treasury: number | null, leadership: number | null): RankedCandidate {
  return {
    candidateId: id,
    personId: `person-${id}`,
    fullName: name,
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
      status: "reviewed",
      updatedAt: null,
      reviewedAt: "2026-11-02T09:00:00Z",
      reviewedByName: "Alok Kumar",
      baselineOverall: 0,
      scoredCount: [treasury, leadership].filter((score) => score !== null).length,
      criteriaCount: 2,
      retiredCount: 1,
      scores: [
        { key: TREASURY.key, score: treasury, hasEvidence: true },
        { key: LEADERSHIP.key, score: leadership, hasEvidence: false },
      ],
    },
  };
}

const MARCUS = candidate("marcus", "Marcus Chen", 9, 7);
const ASHA = candidate("asha", "Asha Mehta", 7, 9);

describe("ranking wording", () => {
  it("states the baseline leader and their lead in points, from the totals alone", () => {
    const rows = rankRows([MARCUS, ASHA], CRITERIA, BASELINE, BASELINE);

    const insight = insightOf(rows, CRITERIA, BASELINE, BASELINE, true)!;

    expect(insight.title).toBe("Asha leads under these priorities");
    expect(insight.lead).toBe("0.0-point lead over Marcus");
    expect(insight.text).toBe("On the agreed 50:50 baseline, Asha scores 80.0 against Marcus's 80.0.");
  });

  it("says what a technical-heavy scenario does to the leader's own technical score", () => {
    const scenario = { ...BASELINE, share: 70 };
    const insight = insightOf(rankRows([MARCUS, ASHA], CRITERIA, BASELINE, scenario), CRITERIA, BASELINE, scenario, false)!;

    expect(insight.title).toBe("Marcus leads under these priorities");
    expect(insight.text).toBe("Technical weighting at 70% favours Marcus's 9.0/10 technical score.");
  });

  it("names the share at which the lead changes hands", () => {
    const standings = rankStandings([MARCUS, ASHA], CRITERIA, BASELINE);
    expect(sensitivityOf(leaderFlips(standings, CRITERIA), standings[0])).toBe(
      "Reference sensitivity: 50.0% technical (Asha → Marcus). Check these thresholds against the competency weights in force before relying on them.",
    );
    expect(sensitivityOf(null, standings[0])).toBeNull();
  });

  it("names who leads across the range, not who leads at a share outside it", () => {
    const technical = candidate("tariq", "Tariq Aziz", 10, 0);
    const balanced = candidate("layla", "Layla Nasser", 8, 9);
    const atNinety = rankStandings([technical, balanced], CRITERIA, { ...BASELINE, share: 90 });

    expect(atNinety[0].candidate.candidateId).toBe("tariq");
    expect(sensitivityOf(leaderFlips(atNinety, CRITERIA), leaderAt(atNinety, SENSITIVITY_FROM))).toBe(
      "Reference sensitivity: Layla leads at every technical weighting from 30% to 80%.",
    );
  });

  it("formats shifts with a sign, and says when a rank holds", () => {
    expect(signedPoints(0.254)).toBe("+0.25 pts");
    expect(signedPoints(-1.5)).toBe("−1.50 pts");
    expect(signedPoints(0.001)).toBe("0.00 pts");
    expect(rankShiftLabel(2)).toBe("Up 2");
    expect(rankShiftLabel(-1)).toBe("Down 1");
    expect(rankShiftLabel(0)).toBe("No change");
  });

  it("compares only what both have scored, and asks for the rest to be scored first", () => {
    const unscored = candidate("omar", "Omar Haddad", 6, null);
    const rows = rankRows([MARCUS, unscored], CRITERIA, BASELINE, BASELINE);

    expect(differentiatorsOf(MARCUS, unscored, CRITERIA)).toBe(
      "Marcus: +3.0 in Treasury. 1 competency is not compared, being unscored on one side.",
    );
    expect(validateOf([rows[0], rows[1]], MARCUS)).toMatch(/^Score the 1 unscored competency before relying on this order\./);
  });

  it("says who reviewed an assessment, how much of it carries evidence and what is not counted", () => {
    expect(evidenceContextOf(MARCUS, () => "2 Nov 2026")).toBe(
      "Marcus: reviewed by Alok Kumar on 2 Nov 2026 · evidence on 1 of 2 · 1 retired score not counted.",
    );
  });
});
