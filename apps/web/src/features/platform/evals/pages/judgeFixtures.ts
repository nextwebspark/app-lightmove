import type {
  EvalCalibrationPair,
  EvalCalibrationPairs,
  EvalRubricDetail,
  EvalRunCaseComparison,
  EvalRunJudged,
} from "../api/types";

/** Test data for the judge's screens: the assessment pack's rubric, checked and counting. */
export const rubric = (overrides: Partial<EvalRubricDetail> = {}): EvalRubricDetail => ({
  id: "rb1",
  version: 1,
  state: "COUNTS",
  thresholdPercent: 80,
  pairsNeeded: 20,
  pairsDrawn: 20,
  pairsLabelled: 20,
  agreed: 35,
  picks: 40,
  agreementPercent: 87.5,
  criteria: [
    { key: "specific", name: "Evidence is specific and names its source", better: "Each claim points at a dated note, a call or a document.", agreed: 18, of: 20 },
    { key: "invented", name: "No invented facts", better: "Nothing is stated that the case does not hold.", agreed: 17, of: 20 },
  ],
  labelledBy: "Noor Rahman",
  labelledAt: "2026-11-05T10:00:00Z",
  checkedAt: "2026-11-06T10:00:00Z",
  checkedJudgeVersion: 2,
  judgeVersion: 2,
  judgeModel: "gemini-3.8-flash",
  judgeTemperature: 0,
  checkJudged: null,
  lastCheckFailed: false,
  pairsAvailable: true,
  differences: [
    { caseKey: "AD-015", criterionKey: "specific", criterion: "Evidence is specific and names its source", person: "B", judge: "TIE", reason: "Both answers name the call." },
    { caseKey: "AD-001", criterionKey: "invented", criterion: "No invented facts", person: "TIE", judge: "A", reason: "B states a figure the case does not hold." },
    { caseKey: "AD-026", criterionKey: "invented", criterion: "No invented facts", person: "A", judge: "TIE", reason: "Neither answer states a fact from outside the case." },
    { caseKey: "AD-009", criterionKey: "specific", criterion: "Evidence is specific and names its source", person: "A", judge: null, reason: null },
  ],
  ...overrides,
});

export const unlabelledRubric = (overrides: Partial<EvalRubricDetail> = {}): EvalRubricDetail =>
  rubric({
    state: "UNLABELLED",
    pairsDrawn: 0,
    pairsLabelled: 0,
    agreed: null,
    picks: null,
    agreementPercent: null,
    criteria: rubric().criteria.map((criterion) => ({ ...criterion, agreed: null, of: null })),
    labelledBy: null,
    labelledAt: null,
    checkedAt: null,
    checkedJudgeVersion: null,
    differences: [],
    ...overrides,
  });

/** A holdout run's judged criteria: 36 pairs, the rubric counting when it finished. */
export const judged = (overrides: Partial<EvalRunJudged> = {}): EvalRunJudged => ({
  rubricVersion: 1,
  judgeVersion: 2,
  judgeModel: "gemini-3.8-flash",
  state: "COUNTS",
  agreementPercent: 87.5,
  counted: true,
  tolerancePoints: 10,
  pairs: 36,
  criteria: [
    { key: "specific", name: "Evidence is specific and names its source", candidateWon: 19, baselineWon: 6, ties: 11, unjudged: 0, net: 36.1, worseBeyondTolerance: false },
    { key: "invented", name: "No invented facts", candidateWon: 5, baselineWon: 4, ties: 27, unjudged: 0, net: 2.8, worseBeyondTolerance: false },
  ],
  ...overrides,
});

/** A case of a prompt answered in words, which the judge alone scored. */
export const judgedCase = (): EvalRunCaseComparison => ({
  caseId: "o14",
  key: "OP-014",
  name: "Recent move to a new company",
  tag: "move",
  outcome: "BETTER",
  checks: [],
  readings: [],
  prose: {
    candidate: "You moved from Aldermoor Power to lead finance at Kestrel Grid in September.",
    baseline: "Your impressive career in energy finance caught my attention.",
  },
  judged: [
    { key: "specific", name: "Specific to the person", result: "CANDIDATE", pairs: 3, of: 3, reason: "B names the move to Kestrel Grid in September." },
    { key: "plain", name: "Plain and brief", result: "TIE", pairs: 2, of: 3, reason: "Both are one sentence." },
  ],
  candidateShownAs: ["B", "A", "B"],
});

export const calibrationPair = (position: number, overrides: Partial<EvalCalibrationPair> = {}): EvalCalibrationPair => ({
  id: `p${position}`,
  position,
  caseKey: `AD-${String(position).padStart(3, "0")}`,
  caseName: `Case ${position}`,
  split: "DEV",
  input: '{\n  "profile": "Chief financial officer"\n}',
  a: { prose: null, readings: [{ label: "Financial stewardship", score: 8, text: "Ran a cost programme (call 2 Oct, 08:40)." }] },
  b: { prose: null, readings: [{ label: "Financial stewardship", score: null, text: null }] },
  picks: {},
  ...overrides,
});

/** Three pairs stand in for the twenty: the first is labelled already. */
export const calibrationPairs = (overrides: Partial<EvalCalibrationPairs> = {}): EvalCalibrationPairs => ({
  datasetId: "d1",
  datasetName: "Assessment draft: pack v1",
  rubricVersion: 1,
  criteria: [
    { key: "specific", name: "Evidence is specific and names its source", better: "Each claim points at a dated note, a call or a document." },
    { key: "invented", name: "No invented facts", better: "Nothing is stated that the case does not hold." },
  ],
  pairsNeeded: 20,
  thresholdPercent: 80,
  pairs: [calibrationPair(1, { picks: { specific: "A", invented: "TIE" } }), calibrationPair(2), calibrationPair(3)],
  ...overrides,
});
