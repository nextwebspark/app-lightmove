import type {
  EvalCaseSummary,
  EvalDatasetOverview,
  EvalRunCaseComparison,
  EvalRunDetail,
  EvalRunEstimate,
  EvalRunOverview,
  EvalRunSummary,
} from "../api/types";

/** Test data for the Evals screens: the assessment draft's pack, and a holdout run of v3 against the live v2. */
export const dataset = (overrides: Partial<EvalDatasetOverview> = {}): EvalDatasetOverview => ({
  id: "d1",
  name: "Assessment draft: pack v1",
  description: null,
  promptId: "candidate-scorecard-draft",
  promptName: "Assessment draft",
  cases: 30,
  dev: 18,
  holdout: 12,
  updatedAt: "2026-11-17T10:00:00Z",
  importable: true,
  ...overrides,
});

export const evalCase = (overrides: Partial<EvalCaseSummary> = {}): EvalCaseSummary => ({
  id: "c1",
  key: "AD-001",
  name: "Strong direct evidence",
  tag: "direct",
  split: "DEV",
  addedBy: "Noor Rahman",
  addedAt: "2026-11-17T10:00:00Z",
  retiredAt: null,
  ...overrides,
});

export const runOverview = (overrides: Partial<EvalRunOverview> = {}): EvalRunOverview => ({
  id: "r42",
  number: 42,
  promptId: "candidate-scorecard-draft",
  promptName: "Assessment draft",
  datasetId: "d1",
  datasetName: "Assessment draft: pack v1",
  split: "HOLDOUT",
  candidateVersion: 3,
  baselineVersion: 2,
  status: "FINISHED",
  verdict: "PASS",
  failure: null,
  cases: 12,
  repeats: 3,
  callsPlanned: 72,
  callsMade: 72,
  inputTokens: 500_000,
  outputTokens: 110_000,
  startedBy: "Noor Rahman",
  createdAt: "2026-11-19T16:20:00Z",
  startedAt: "2026-11-19T16:20:01Z",
  finishedAt: "2026-11-19T16:26:00Z",
  ...overrides,
});

export const summary = (overrides: Partial<EvalRunSummary> = {}): EvalRunSummary => ({
  tolerancePoints: 3,
  checks: [
    { id: "competency-ids", label: "Every competency id is valid", candidate: 100, baseline: 100, change: 0, worseBeyondTolerance: false },
    { id: "evidence-names-source", label: "Evidence names its source", candidate: 97.2, baseline: 88.9, change: 8.3, worseBeyondTolerance: false },
    { id: "score-in-band", label: "Score inside the labelled band", candidate: 86.1, baseline: 88.9, change: -2.8, worseBeyondTolerance: false },
  ],
  candidate: { sameAnswerRate: 80, scoreSpread: 0.4 },
  baseline: { sameAnswerRate: 75, scoreSpread: 0.6 },
  regressedCases: 1,
  betterCases: 1,
  unchangedCases: 10,
  ...overrides,
});

export const runDetail = (overrides: Partial<EvalRunDetail> = {}): EvalRunDetail => ({
  run: runOverview(),
  candidate: { version: 3, live: false, modelId: "gemini-3.8-flash", temperature: 0.2 },
  baseline: { version: 2, live: true, modelId: "gemini-3.5-flash", temperature: 0 },
  countsAtPutLive: true,
  callCap: 200,
  secondsLeft: null,
  summary: summary(),
  ...overrides,
});

export const changedCases = (): EvalRunCaseComparison[] => [
  {
    caseId: "c7",
    key: "AD-007",
    name: "Conflicting sources",
    tag: "conflict",
    outcome: "REGRESSION",
    checks: [{ id: "score-in-band", label: "Score inside the labelled band", candidatePassed: 0, baselinePassed: 3, repeats: 3 }],
    readings: [
      {
        key: "T1",
        label: "Financial stewardship",
        expected: "7 to 9",
        candidate: { mean: 6, spread: 0, outside: true, text: "The 2024 note says the refinancing stalled (note, 14 Feb 2024)." },
        baseline: { mean: 8, spread: 0.5, outside: false, text: "Closed the refinancing in March 2026 (call 9 Oct, 22:10)." },
      },
      {
        key: "B2",
        label: "Board and investor relations",
        expected: "No evidence",
        candidate: { mean: null, spread: null, outside: false, text: null },
        baseline: { mean: null, spread: null, outside: false, text: null },
      },
    ],
  },
  {
    caseId: "c22",
    key: "AD-022",
    name: "Fake email and phone in a note",
    tag: "contact",
    outcome: "BETTER",
    checks: [{ id: "evidence-names-source", label: "Evidence names its source", candidatePassed: 3, baselinePassed: 1, repeats: 3 }],
    readings: [],
  },
];

export const estimate = (overrides: Partial<EvalRunEstimate> = {}): EvalRunEstimate => ({
  baselineVersion: 2,
  cases: 12,
  repeats: 3,
  repeatsReason: "PROMPT_VARIES",
  calls: 72,
  tokens: 610_000,
  minutes: 6,
  callCap: 200,
  tooLarge: false,
  ...overrides,
});
