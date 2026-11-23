import { describe, expect, it } from "vitest";
import type { PromotionGate } from "../../prompts/api/types";
import { changedCases, runDetail, runOverview, summary } from "../pages/evalFixtures";
import {
  changeLabel,
  checkNote,
  checksFootnote,
  costLabel,
  gateFailedLine,
  gatePassedLine,
  percentLabel,
  repeatsReasonLine,
  runLabel,
  runTitle,
  scoreLabel,
  timeLeftLabel,
  tokensLabel,
  verdictLine,
  whatChanged,
} from "./evalLabels";

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
  regressedCases: 1,
  failedChecks: [],
  datasetId: "d1",
  judged: null,
  ...overrides,
});

describe("evalLabels", () => {
  it("names a run and what it cost", () => {
    expect(runLabel(42)).toBe("R-0042");
    expect(tokensLabel(610_000)).toBe("0.61M tokens");
    expect(tokensLabel(15_478)).toBe("15k tokens");
    expect(costLabel(runOverview())).toBe("72 calls · 0.61M tokens");
    expect(costLabel(runOverview({ status: "CANCELLED", callsMade: 31, callsPlanned: 160 }))).toBe("31 of 160 calls");
    // An answer put back to the model takes the calls past the plan, which a progress line never shows.
    expect(costLabel(runOverview({ status: "RUNNING", callsMade: 75 }))).toBe("72 of 72 calls");
    expect(runTitle(runDetail())).toBe("Assessment draft · v3 against v2 (live)");
  });

  it("writes a check's rates and its change the way the table shows them", () => {
    expect(percentLabel(97.2)).toBe("97.2%");
    expect(percentLabel(100)).toBe("100%");
    expect(percentLabel(null)).toBe("Nothing to judge");
    expect(changeLabel(8.3)).toBe("+8.3 pts");
    expect(changeLabel(-5)).toBe("−5 pts");
    expect(changeLabel(0)).toBe("Same");
    expect(changeLabel(null)).toBe("");
    const worse = { ...summary().checks[2], worseBeyondTolerance: true };
    expect(checkNote(worse, 3, "the live version")).toBe("Outside the tolerance of 3 points");
    expect(checkNote(worse, 0, "the live version")).toBe("Worse than the live version");
    expect(checkNote(summary().checks[2], 3, "the live version")).toBe("");
  });

  it("says what a passing run means for putting the candidate live, and when it means nothing", () => {
    expect(verdictLine(runDetail())).toBe(
      "Across the holdout cases, the candidate is no worse than the live version on the 3 checks. One case regressed, inside the tolerance; it is listed first below. The candidate can be put live without a written reason.",
    );
    expect(verdictLine(runDetail({ run: runOverview({ split: "DEV" }), countsAtPutLive: false }))).toContain(
      "Only a run on the holdout cases counts when a version is put live.",
    );
    expect(verdictLine(runDetail({ countsAtPutLive: false }))).toContain("still to be reviewed");
    const againstAnother = runDetail({ baseline: { version: 1, live: false, modelId: null, temperature: 0 } });
    expect(verdictLine(againstAnother)).toContain("no worse than v1 on the 3 checks.");
    expect(verdictLine(againstAnother)).not.toContain("without a written reason");
  });

  it("counts the checks a failed run fell on", () => {
    const failed = runDetail({
      run: runOverview({ verdict: "FAIL" }),
      summary: summary({
        checks: summary().checks.map((check, index) => ({ ...check, worseBeyondTolerance: index > 0 })),
      }),
    });

    expect(verdictLine(failed)).toBe(
      "The candidate is worse than the live version on two checks. Putting it live needs a written reason, which is recorded.",
    );
  });

  it("says in one line what changed in a case: a score across its band, else the check that moved", () => {
    const [regressed, better] = changedCases();

    expect(whatChanged(regressed, "the live version")).toBe(
      "Financial stewardship fell from 8 to 6. The labelled band is 7 to 9.",
    );
    expect(whatChanged(better, "the live version")).toBe(
      "Evidence names its source: 3 of 3 answers pass, against 1 for the live version.",
    );
    expect(scoreLabel(regressed.readings[0].baseline)).toBe("8.0 ± 0.5");
    expect(scoreLabel({ mean: 6, spread: null, outside: false, text: null })).toBe("6.0");
    expect(scoreLabel(regressed.readings[1].candidate)).toBe("No evidence");
  });

  it("writes the line under the checks: the repeats, the spread, what answered and the cost", () => {
    const names: Record<string, string> = { "gemini-3.8-flash": "Gemini 3.8 Flash", "gemini-3.5-flash": "Gemini 3.5 Flash" };

    expect(checksFootnote(runDetail(), (modelId) => names[modelId ?? ""] ?? "the default model")).toBe(
      "Share of answers that pass, as the mean of 3 repeats. Score spread across the 3 repeats: ±0.4 for the candidate, ±0.6 for the live version. Candidate on Gemini 3.8 Flash, temperature 0.2 · Live on Gemini 3.5 Flash, temperature 0. 72 calls · 0.61M tokens.",
    );
  });

  it("writes the put-live check's two findings", () => {
    expect(gatePassedLine(gate())).toBe(
      "12 cases, 3 repeats each. Better on 5 checks, the same on 3, worse on none. One case regressed inside the tolerance.",
    );
    expect(gatePassedLine(gate({ repeats: 1, checksBetter: 0, checksSame: 2, regressedCases: 0, cases: 4 }))).toBe(
      "4 cases, answered once. Better on no checks, the same on 2, worse on none.",
    );
    expect(
      gateFailedLine(
        gate({
          state: "FAILED",
          failedChecks: [
            { label: "Score inside the labelled band", candidate: 75, baseline: 83 },
            { label: "Self-report alone stays at 7 or below", candidate: 83, baseline: 92 },
          ],
        }),
      ),
    ).toBe(
      "Worse than the live version on two checks: score inside the labelled band (75% against 83%) and self-report alone stays at 7 or below (83% against 92%).",
    );
  });

  it("explains the repeats and the time left", () => {
    expect(repeatsReasonLine("PROMPT_VARIES", 3)).toBe("This prompt is always answered 3 times, because its answers vary.");
    expect(repeatsReasonLine("TEMPERATURE", 3)).toContain("above temperature 0");
    expect(repeatsReasonLine("ONCE", 1)).toBe("");
    expect(timeLeftLabel(170)).toBe("about 3 minutes left");
    expect(timeLeftLabel(20)).toBe("less than a minute left");
    expect(timeLeftLabel(null)).toBeNull();
  });
});
