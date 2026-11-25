export type EvalCaseSplit = "DEV" | "HOLDOUT";
export type EvalRunStatus = "QUEUED" | "RUNNING" | "FINISHED" | "CANCELLED" | "FAILED";
export type EvalRunVerdict = "PASS" | "FAIL";
export type EvalRunFailure = "CALL_CAP_REACHED" | "MODEL_UNAVAILABLE" | "STOPPED_UNEXPECTEDLY";
export type EvalRepeatsReason = "ONCE" | "TEMPERATURE" | "PROMPT_VARIES";
export type EvalCaseOutcome = "REGRESSION" | "BETTER";
/** Only `COUNTS` lets a rubric's judged criteria change a run's result; in every other state they are shown only. */
export type RubricState = "COUNTS" | "SHOWN_ONLY" | "UNLABELLED" | "UNCHECKED" | "STALE" | "CHECKING";
/** Which of a pair's two answers is the better on one criterion. */
export type JudgePick = "A" | "B" | "TIE";
export type JudgedWinner = "CANDIDATE" | "BASELINE" | "TIE";

export interface EvalRubricSummary {
  version: number;
  state: RubricState;
}

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
  /** Null: no judge scores this dataset. */
  rubric: EvalRubricSummary | null;
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

export interface RubricCriterion {
  key: string;
  name: string;
  better: string;
}

/** `agreed` of `of` labelled pairs the judge picked as the person did; both null before its first check. */
export interface EvalRubricCriterion extends RubricCriterion {
  agreed: number | null;
  of: number | null;
}

/** `judge` null: it gave no pick that could be read. */
export interface EvalRubricDifference {
  caseKey: string;
  criterionKey: string;
  criterion: string;
  person: JudgePick;
  judge: JudgePick | null;
  reason: string | null;
}

/**
 * `agreed` of `picks` are the last finished check's. `checkedJudgeVersion` made them; `judgeVersion` serves now.
 * `checkJudged` counts the pairs a check that is going has judged.
 */
export interface EvalRubricDetail {
  id: string;
  version: number;
  state: RubricState;
  thresholdPercent: number;
  pairsNeeded: number;
  pairsDrawn: number;
  pairsLabelled: number;
  agreed: number | null;
  picks: number | null;
  agreementPercent: number | null;
  criteria: EvalRubricCriterion[];
  labelledBy: string | null;
  labelledAt: string | null;
  checkedAt: string | null;
  checkedJudgeVersion: number | null;
  judgeVersion: number | null;
  judgeModel: string | null;
  judgeTemperature: number | null;
  checkJudged: number | null;
  lastCheckFailed: boolean;
  pairsAvailable: boolean;
  differences: EvalRubricDifference[];
}

export interface EvalDatasetDetail {
  dataset: EvalDatasetOverview;
  cases: EvalCaseSummary[];
  rubric: EvalRubricDetail | null;
}

/** One of a pair's answers: its words, or what it scored. Never which version wrote it. */
export interface EvalCalibrationAnswer {
  prose: string | null;
  readings: { label: string; score: number | null; text: string | null }[];
}

export interface EvalCalibrationPair {
  id: string;
  position: number;
  caseKey: string;
  caseName: string;
  split: EvalCaseSplit;
  input: string;
  a: EvalCalibrationAnswer;
  b: EvalCalibrationAnswer;
  /** The person's picks so far, by criterion key. */
  picks: Record<string, JudgePick>;
}

export interface EvalCalibrationPairs {
  datasetId: string;
  datasetName: string;
  rubricVersion: number;
  criteria: RubricCriterion[];
  pairsNeeded: number;
  thresholdPercent: number;
  pairs: EvalCalibrationPair[];
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
  /** Of `calls`, those the judge makes: one for each pair of answers. */
  judgeCalls: number;
  /** Null: the dataset has no rubric. */
  judgedCounts: boolean | null;
  tokens: number;
  minutes: number;
  callCap: number;
  tooLarge: boolean;
}

/**
 * `callsMade` counts an answer put back to the model, so it can pass `callsPlanned`. A finished run with a null
 * `verdict` gave no result.
 */
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
  /** The dataset has a rubric, so a judge compares each pair of answers. */
  judged: boolean;
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
  /** Null: the dataset had no rubric. */
  judged: EvalRunJudged | null;
}

/** Counted in pairs. `net` is the candidate's share less the baseline's, in points; null when none was judged. */
export interface JudgedCriterionScore {
  key: string;
  name: string;
  candidateWon: number;
  baselineWon: number;
  ties: number;
  unjudged: number;
  net: number | null;
  worseBeyondTolerance: boolean;
}

/** What the judge found, and whether it counted, as things stood when the run finished. */
export interface EvalRunJudged {
  rubricVersion: number;
  judgeVersion: number | null;
  judgeModel: string | null;
  state: RubricState;
  agreementPercent: number | null;
  counted: boolean;
  tolerancePoints: number;
  pairs: number;
  criteria: JudgedCriterionScore[];
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
  /** Both answers as written, for a prompt answered in words. */
  prose: { candidate: string | null; baseline: string | null } | null;
  judged: EvalCaseJudgedCriterion[];
  /** What the candidate's answer was shown to the judge as, by repeat. */
  candidateShownAs: ("A" | "B")[];
}

/** The side most of a case's pairs went to on one criterion, and the judge's reason on the first. */
export interface EvalCaseJudgedCriterion {
  key: string;
  name: string;
  result: JudgedWinner;
  pairs: number;
  of: number;
  reason: string | null;
}

export interface EvalRunCases {
  changed: EvalRunCaseComparison[];
  unchanged: number;
}
