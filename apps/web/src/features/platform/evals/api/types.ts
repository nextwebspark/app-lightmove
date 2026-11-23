export type EvalCaseSplit = "DEV" | "HOLDOUT";
export type EvalRunStatus = "QUEUED" | "RUNNING" | "FINISHED" | "CANCELLED" | "FAILED";
export type EvalRunVerdict = "PASS" | "FAIL";
export type EvalRunFailure = "CALL_CAP_REACHED" | "MODEL_UNAVAILABLE" | "STOPPED_UNEXPECTEDLY";
export type EvalRepeatsReason = "ONCE" | "TEMPERATURE" | "PROMPT_VARIES";
export type EvalCaseOutcome = "REGRESSION" | "BETTER";

/** The three counts leave retired cases out. `importable` false: the prompt cannot take cases or be run. */
export interface EvalDatasetOverview {
  id: string;
  name: string;
  description: string | null;
  promptId: string;
  promptName: string;
  cases: number;
  dev: number;
  holdout: number;
  updatedAt: string;
  importable: boolean;
}

/** `addedBy` null: shipped with a release. */
export interface EvalCaseSummary {
  id: string;
  key: string;
  name: string;
  tag: string | null;
  split: EvalCaseSplit;
  addedBy: string | null;
  addedAt: string;
  retiredAt: string | null;
}

export interface EvalDatasetDetail {
  dataset: EvalDatasetOverview;
  cases: EvalCaseSummary[];
}

/** `line` is the row's line in the file. Nothing is added while `problems` is not empty. */
export interface EvalCaseImportResult {
  committed: boolean;
  rows: number;
  added: number;
  alreadyPresent: number;
  dev: number;
  holdout: number;
  problems: { line: number; message: string }[];
}

/** `baselineVersion` left out is the version serving now. */
export interface StartEvalRunRequest {
  datasetId: string;
  split: EvalCaseSplit;
  candidateVersion: number;
  baselineVersion?: number;
}

export interface EvalRunEstimate {
  baselineVersion: number;
  cases: number;
  repeats: number;
  repeatsReason: EvalRepeatsReason;
  calls: number;
  tokens: number;
  minutes: number;
  callCap: number;
  tooLarge: boolean;
}

/** `callsMade` counts an answer put back to the model, so it can pass `callsPlanned`. */
export interface EvalRunOverview {
  id: string;
  number: number;
  promptId: string;
  promptName: string;
  datasetId: string;
  datasetName: string;
  split: EvalCaseSplit;
  candidateVersion: number;
  baselineVersion: number;
  status: EvalRunStatus;
  verdict: EvalRunVerdict | null;
  failure: EvalRunFailure | null;
  cases: number;
  repeats: number;
  callsPlanned: number;
  callsMade: number;
  inputTokens: number;
  outputTokens: number;
  startedBy: string | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
}

export interface EvalRunSide {
  version: number;
  live: boolean;
  modelId: string | null;
  temperature: number | null;
}

/** Pass rates in percent; a side is null when no answer gave the check anything to judge. */
export interface EvalCheckScore {
  id: string;
  label: string;
  candidate: number | null;
  baseline: number | null;
  change: number | null;
  worseBeyondTolerance: boolean;
}

export interface EvalSideStability {
  sameAnswerRate: number | null;
  scoreSpread: number | null;
}

export interface EvalRunSummary {
  tolerancePoints: number;
  checks: EvalCheckScore[];
  candidate: EvalSideStability;
  baseline: EvalSideStability;
  regressedCases: number;
  betterCases: number;
  unchangedCases: number;
}

/** `summary` is null until the run finishes; `secondsLeft` unless it is going and has answered something. */
export interface EvalRunDetail {
  run: EvalRunOverview;
  candidate: EvalRunSide;
  baseline: EvalRunSide;
  countsAtPutLive: boolean;
  callCap: number;
  secondsLeft: number | null;
  summary: EvalRunSummary | null;
}

export interface EvalCaseCheckChange {
  id: string;
  label: string;
  candidatePassed: number;
  baselinePassed: number;
  repeats: number;
}

export interface EvalReadingSide {
  mean: number | null;
  spread: number | null;
  outside: boolean;
  text: string | null;
}

export interface EvalReadingComparison {
  key: string;
  label: string;
  expected: string | null;
  candidate: EvalReadingSide;
  baseline: EvalReadingSide;
}

export interface EvalRunCaseComparison {
  caseId: string;
  key: string;
  name: string;
  tag: string | null;
  outcome: EvalCaseOutcome;
  checks: EvalCaseCheckChange[];
  readings: EvalReadingComparison[];
}

export interface EvalRunCases {
  changed: EvalRunCaseComparison[];
  unchanged: number;
}
