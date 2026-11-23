import { describe, expect, it } from "vitest";
import type { PromotionGate } from "../../prompts/api/types";
import { rubric, unlabelledRubric } from "../pages/judgeFixtures";
import { gateFailedLine, gateJudgeOnlyLine, gateNoResultLine, gatePassedLine, isJudgeOnlyGate } from "./evalLabels";
import {
  caseJudgedResult,
  judgeCallsLine,
  judgedChange,
  judgeOrderLine,
  netLabel,
  rubricCellLabel,
  rubricMeta,
  rubricText,
  rubricTitle,
  whyNotCounting,
} from "./judgeLabels";

const gate = (overrides: Partial<PromotionGate> = {}): PromotionGate => ({
  state: "PASSED",
  runId: "r42",
  runNumber: 42,
  finishedAt: "2026-11-19T16:26:00Z",
  cases: 12,
  repeats: 3,
  checksBetter: 5,
  checksSame: 3,
  checksWorse: 0,
  regressedCases: 0,
  failedChecks: [],
  datasetId: "d1",
  judged: { counted: true, state: "COUNTS", criteria: 4, forCandidate: 2, forBaseline: [] },
  ...overrides,
});

describe("judgeLabels", () => {
  it("names a dataset's rubric in the list by its version and whether it counts", () => {
    expect(rubricCellLabel(null)).toBe("None");
    expect(rubricCellLabel({ version: 1, state: "COUNTS" })).toBe("v1 · counts");
    expect(rubricCellLabel({ version: 2, state: "STALE" })).toBe("v2 · shown only");
  });

  it("says why a rubric does not count by what would change it", () => {
    expect(whyNotCounting("SHOWN_ONLY")).toBe("while the judge agrees with a person on less than 80% of the labelled picks");
    expect(whyNotCounting("STALE")).toBe("until the judge is checked again on the labelled pairs");
    expect(whyNotCounting("UNLABELLED")).toBe(
      "until a person has labelled 20 pairs and the judge has been checked against them",
    );
  });

  it("tells a label that changed from a judge that changed", () => {
    const relabelled = rubric({ state: "STALE" });
    expect(rubricTitle(relabelled)).toBe("A label changed, so the judged criteria are shown only");
    expect(rubricText(relabelled)).toBe(
      "Agreement was 88%, and a label has changed since. Check the judge again on the 20 labelled pairs.",
    );
    expect(rubricTitle(rubric({ state: "STALE", checkedJudgeVersion: 1 }))).toBe(
      "The judge changed, so the judged criteria are shown only",
    );
  });

  it("says how far labelling has got, and where the pairs come from before any is labelled", () => {
    expect(rubricText(unlabelledRubric({ pairsDrawn: 20, pairsLabelled: 6 }))).toMatch(/^6 of 20 pairs are labelled\./);
    expect(rubricMeta(unlabelledRubric())).toBe(
      "The pairs are answers two versions gave to the same dev case in a finished run.",
    );
    expect(rubricText(rubric({ state: "UNCHECKED" }))).toMatch(/^All 20 pairs are labelled\. Check the judge against them/);
  });

  it("writes a criterion's net in whole points", () => {
    expect(netLabel(36.1)).toBe("+36 pts");
    expect(netLabel(-25)).toBe("−25 pts");
    expect(netLabel(0.3)).toBe("Same");
    expect(netLabel(null)).toBe("");
  });

  it("tells a case's pairs by the side they went to, against a version that is not live too", () => {
    const row = { key: "specific", name: "Specific to the person", result: "BASELINE" as const, pairs: 2, of: 3, reason: null };
    expect(caseJudgedResult(row, "Live")).toBe("Live better · 2 of 3 pairs");
    expect(caseJudgedResult(row, "v1")).toBe("v1 better · 2 of 3 pairs");
    expect(judgedChange([row], "the live version")).toBe(
      "Specific to the person: the judge preferred the live version on 2 of 3 pairs.",
    );
    expect(judgeOrderLine(["A"], "v1")).toContain("pair 1, candidate as A and v1 as B");
  });

  it("adds the judge's calls to a run's estimate only where there are any", () => {
    expect(judgeCallsLine(0)).toBe("");
    expect(judgeCallsLine(36)).toBe("36 of the calls are the judge's, one for each pair of answers.");
  });

  it("adds what the judge found to the check at put live, and says when it did not count", () => {
    expect(gatePassedLine(gate())).toBe(
      "12 cases, 3 repeats each. Better on 5 checks, the same on 3, worse on none. The judge prefers the candidate on 2 of the 4 judged criteria and the live version on none.",
    );
    expect(gatePassedLine(gate({ judged: { counted: false, state: "UNLABELLED", criteria: 4, forCandidate: 2, forBaseline: [] } }))).toBe(
      "12 cases, 3 repeats each. Better on 5 checks, the same on 3, worse on none. The judged criteria are shown on the run and do not count.",
    );
    expect(
      gateFailedLine(
        gate({
          state: "FAILED",
          failedChecks: [{ label: "Score inside the labelled band", candidate: 75, baseline: 83 }],
          judged: { counted: true, state: "COUNTS", criteria: 4, forCandidate: 0, forBaseline: ["Nothing over-claimed from self-report"] },
        }),
      ),
    ).toBe(
      "Worse than the live version on one check: score inside the labelled band (75% against 83%). The judge prefers the live version on one criterion: nothing over-claimed from self-report.",
    );
  });

  it("words a run the judge alone scored, passed or with no result", () => {
    const judgeOnly = gate({
      cases: 10,
      checksBetter: 0,
      checksSame: 0,
      checksWorse: 0,
      judged: { counted: true, state: "COUNTS", criteria: 3, forCandidate: 1, forBaseline: [] },
    });
    expect(isJudgeOnlyGate(judgeOnly)).toBe(true);
    expect(isJudgeOnlyGate(gate())).toBe(false);
    expect(gateJudgeOnlyLine(judgeOnly)).toBe(
      "10 cases, 3 repeats each. This prompt has no checks, so the judge alone scores it: it prefers the candidate on 1 of the 3 judged criteria and the live version on none.",
    );
    expect(
      gateNoResultLine(
        gate({ state: "NO_RESULT", judged: { counted: false, state: "STALE", criteria: 3, forCandidate: 1, forBaseline: [] } }),
        "Outreach opener",
      ),
    ).toBe(
      "Outreach opener has no checks, and its judged criteria do not count until the judge is checked again on the labelled pairs. Nothing yet shows this version is no worse than the live one.",
    );
  });
});
