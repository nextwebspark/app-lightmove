import { formatDate, formatInstantDateTime } from "../../../../lib/datetime/format";
import { formatNumber } from "../../../../lib/format";
import type { PromotionGate } from "../../prompts/api/types";
import { gateJudgedLine, judgedChange, whyNotCounting } from "./judgeLabels";
import type {
  EvalCaseSplit,
  EvalCheckScore,
  EvalReadingSide,
  EvalRepeatsReason,
  EvalRunCaseComparison,
  EvalRunDetail,
  EvalRunFailure,
  EvalRunOverview,
  EvalRunStatus,
} from "../api/types";

export const EVALS_PATH = "/platform/evals";
/** The Evals page opened on its Runs tab. */
export const EVAL_RUNS_PATH = `${EVALS_PATH}?tab=runs`;
export const evalDatasetPath = (datasetId: string) => `${EVALS_PATH}/datasets/${encodeURIComponent(datasetId)}`;
export const DATASET_VIEW_PARAM = "view";
/** A dataset opened on its Rubric view. */
export const evalRubricPath = (datasetId: string) => `${evalDatasetPath(datasetId)}?${DATASET_VIEW_PARAM}=rubric`;
export const evalCalibrationPath = (datasetId: string) => `${evalDatasetPath(datasetId)}/calibration`;
export const evalRunPath = (runId: string) => `${EVALS_PATH}/runs/${encodeURIComponent(runId)}`;

export const SPLIT_LABEL: Record<EvalCaseSplit, string> = { DEV: "Dev", HOLDOUT: "Holdout" };

export const STATUS_LABEL: Record<EvalRunStatus, string> = {
  QUEUED: "Waiting to start",
  RUNNING: "Running",
  FINISHED: "Finished",
  CANCELLED: "Cancelled",
  FAILED: "Stopped",
};

const FAILURE_LINE: Record<EvalRunFailure, string> = {
  CALL_CAP_REACHED: "This run reached the most calls one run may make with cases still to answer.",
  MODEL_UNAVAILABLE: "The model did not answer, so this run stopped.",
  STOPPED_UNEXPECTEDLY: "This run stopped unexpectedly.",
};

const SMALL_NUMBERS = ["no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];

/** "R-0042": what a person calls a run. */
export function runLabel(number: number): string {
  return `R-${String(number).padStart(4, "0")}`;
}

export function isGoing(status: EvalRunStatus): boolean {
  return status === "QUEUED" || status === "RUNNING";
}

/** "v10 against v7". */
export function versionsLabel(run: Pick<EvalRunOverview, "candidateVersion" | "baselineVersion">): string {
  return `v${run.candidateVersion} against v${run.baselineVersion}`;
}

/** "0.61M tokens", "15k tokens", "640 tokens". */
export function tokensLabel(tokens: number): string {
  if (tokens >= 100_000) return `${(tokens / 1_000_000).toFixed(2)}M tokens`;
  if (tokens >= 1_000) return `${Math.round(tokens / 1_000)}k tokens`;
  return `${tokens} tokens`;
}

/** What a run cost once it finished, or how far it got. */
export function costLabel(run: EvalRunOverview): string {
  if (run.status === "FINISHED") {
    return `${countOf(run.callsMade, "call")} · ${tokensLabel(run.inputTokens + run.outputTokens)}`;
  }
  return progressLabel(run);
}

/** "41 of 72 calls". An answer put back to the model can take the count past the plan, which is not shown. */
export function progressLabel(run: Pick<EvalRunOverview, "callsMade" | "callsPlanned">): string {
  return `${Math.min(run.callsMade, run.callsPlanned)} of ${countOf(run.callsPlanned, "call")}`;
}

/** "Noor Rahman · 19 Nov 2026, 16:20". */
export function startedLabel(run: Pick<EvalRunOverview, "startedBy" | "createdAt">): string {
  const at = formatInstantDateTime(run.createdAt) ?? "";
  return run.startedBy ? `${run.startedBy} · ${at}` : at;
}

export function timeLeftLabel(secondsLeft: number | null): string | null {
  if (secondsLeft === null) return null;
  if (secondsLeft < 60) return "less than a minute left";
  const minutes = Math.round(secondsLeft / 60);
  return `about ${countOf(minutes, "minute")} left`;
}

export function percentLabel(rate: number | null): string {
  return rate === null ? "Nothing to judge" : `${formatNumber(Math.round(rate * 10) / 10)}%`;
}

/** "+8 pts", "−5 pts", "Same"; empty when one side had nothing to judge. */
export function changeLabel(change: number | null): string {
  if (change === null) return "";
  if (change === 0) return "Same";
  return `${change > 0 ? "+" : "−"}${formatNumber(Math.abs(change))} pts`;
}

/** What the note beside a check says when the candidate fell too far on it. */
export function checkNote(check: EvalCheckScore, tolerancePoints: number, baselineName: string): string {
  if (!check.worseBeyondTolerance) return "";
  return tolerancePoints > 0
    ? `Outside the tolerance of ${countOf(tolerancePoints, "point")}`
    : `Worse than ${baselineName}`;
}

/** What the run was measured against, as a sentence names it. */
export function baselineName(detail: Pick<EvalRunDetail, "baseline">): string {
  return detail.baseline.live ? "the live version" : `v${detail.baseline.version}`;
}

/** "Assessment draft · v10 against v7 (live)". */
export function runTitle(detail: EvalRunDetail): string {
  return `${detail.run.promptName} · ${versionsLabel(detail.run)}${detail.baseline.live ? " (live)" : ""}`;
}

/** "R-0042 · Holdout split · 12 cases × 3 repeats · started by Noor Rahman, 19 Nov 2026, 16:20". */
export function runMeta(run: EvalRunOverview): string {
  const at = formatInstantDateTime(run.createdAt) ?? "";
  const size = `${countOf(run.cases, "case")} × ${countOf(run.repeats, "repeat")}`;
  const started = run.startedBy ? `started by ${run.startedBy}, ${at}` : `started ${at}`;
  return `${runLabel(run.number)} · ${SPLIT_LABEL[run.split]} split · ${size} · ${started}`;
}

export function failureLine(failure: EvalRunFailure | null): string {
  return `${FAILURE_LINE[failure ?? "STOPPED_UNEXPECTEDLY"]} It gives no result, so it cannot support putting a version live.`;
}

/** The sentence over a finished run's checks: what it found, and what that means for putting the candidate live. */
export function verdictLine(detail: EvalRunDetail): string {
  const { run, summary } = detail;
  if (!summary) return "";
  const against = baselineName(detail);
  const judged = summary.judged;
  const counted = judged?.counted ? judged.criteria : [];
  const shownOnly = judged && !judged.counted ? " The judged criteria are shown and do not count." : "";
  if (run.verdict === null) {
    return `This run gives no result. ${run.promptName} has no checks, and its judged criteria do not count ${whyNotCounting(judged?.state ?? "UNLABELLED")}. Putting the candidate live needs a written reason, which is recorded.`;
  }
  if (run.verdict === "FAIL") {
    const worse = summary.checks.filter((check) => check.worseBeyondTolerance).length;
    const judgedWorse = counted.filter((criterion) => criterion.worseBeyondTolerance).length;
    const on = [
      worse > 0 ? `${countWord(worse)} ${worse === 1 ? "check" : "checks"}` : "",
      judgedWorse > 0 ? `${countWord(judgedWorse)} judged ${judgedWorse === 1 ? "criterion" : "criteria"}` : "",
    ].filter(Boolean);
    return `The candidate is worse than ${against} on ${on.join(" and ")}.${shownOnly} Putting it live needs a written reason, which is recorded.`;
  }
  const measured = [
    summary.checks.length > 0 ? `the ${countOf(summary.checks.length, "check")}` : "",
    counted.length > 0 ? `the ${counted.length} judged ${counted.length === 1 ? "criterion" : "criteria"}` : "",
  ].filter(Boolean);
  const parts = [
    `Across the ${SPLIT_LABEL[run.split].toLowerCase()} cases, the candidate is no worse than ${against} on ${measured.join(" and ")}.${shownOnly}`,
  ];
  if (summary.regressedCases > 0) {
    const one = summary.regressedCases === 1;
    parts.push(
      `${capitalised(countWord(summary.regressedCases))} ${one ? "case" : "cases"} regressed, inside the tolerance; ${one ? "it is" : "they are"} listed first below.`,
    );
  }
  if (run.split === "DEV") {
    parts.push("Only a run on the holdout cases counts when a version is put live.");
  } else if (!detail.countsAtPutLive) {
    parts.push("This dataset's cases are still to be reviewed, so the run does not count when a version is put live.");
  } else if (detail.baseline.live) {
    parts.push("The candidate can be put live without a written reason.");
  }
  return parts.join(" ");
}

/** The line under the checks table: how the rates were taken, how far scores moved, and what answered. */
export function checksFootnote(detail: EvalRunDetail, modelName: (modelId: string | null) => string): string {
  const { run, summary, candidate, baseline } = detail;
  const parts = [
    run.repeats > 1 ? `Share of answers that pass, as the mean of ${run.repeats} repeats.` : "Share of answers that pass.",
  ];
  if (summary && summary.candidate.scoreSpread !== null && summary.baseline.scoreSpread !== null) {
    parts.push(
      `Score spread across the ${run.repeats} repeats: ±${summary.candidate.scoreSpread.toFixed(1)} for the candidate, ±${summary.baseline.scoreSpread.toFixed(1)} for ${baselineName(detail)}.`,
    );
  }
  const side = (label: string, version: EvalRunDetail["candidate"]) =>
    `${label} on ${modelName(version.modelId)}${version.temperature === null ? "" : `, temperature ${formatNumber(version.temperature)}`}`;
  parts.push(`${side("Candidate", candidate)} · ${side(baseline.live ? "Live" : `v${baseline.version}`, baseline)}.`);
  parts.push(`${costLabel(run)}.`);
  return parts.join(" ");
}

/** "6.0 ± 0.5", "6.0" with one repeat, "No evidence" where no answer scored it. */
export function scoreLabel(side: EvalReadingSide): string {
  if (side.mean === null) return "No evidence";
  return side.spread === null ? side.mean.toFixed(1) : `${side.mean.toFixed(1)} ± ${side.spread.toFixed(1)}`;
}

/**
 * One sentence on what changed in a case: a score that moved across its band, else the first check that moved,
 * else what the judge preferred.
 */
export function whatChanged(compared: EvalRunCaseComparison, against: string): string {
  const moved = compared.readings.find(
    (reading) =>
      reading.candidate.outside !== reading.baseline.outside &&
      reading.candidate.mean !== null &&
      reading.baseline.mean !== null &&
      reading.candidate.mean !== reading.baseline.mean,
  );
  if (moved && moved.candidate.mean !== null && moved.baseline.mean !== null) {
    const verb = moved.candidate.mean < moved.baseline.mean ? "fell" : "rose";
    const band = moved.expected === null ? "" : ` The labelled band is ${moved.expected}.`;
    return `${moved.label} ${verb} from ${formatNumber(moved.baseline.mean)} to ${formatNumber(moved.candidate.mean)}.${band}`;
  }
  const check = compared.checks[0];
  if (!check) return judgedChange(compared.judged, against);
  return `${check.label}: ${check.candidatePassed} of ${countOf(check.repeats, "answer")} ${check.candidatePassed === 1 ? "passes" : "pass"}, against ${check.baselinePassed} for ${against}.`;
}

/** Why each case is answered as often as it is, for the estimate. Empty when it is answered once. */
export function repeatsReasonLine(reason: EvalRepeatsReason, repeats: number): string {
  if (reason === "PROMPT_VARIES") return `This prompt is always answered ${repeats} times, because its answers vary.`;
  if (reason === "TEMPERATURE") {
    return `Each case is answered ${repeats} times, because a version answers above temperature 0.`;
  }
  return "";
}

/** "Run R-0042 · holdout · 19 Nov 2026". */
export function gateRunLine(gate: PromotionGate): string {
  return `Run ${runLabel(gate.runNumber ?? 0)} · holdout · ${formatDate(gate.finishedAt ?? "")}`;
}

/** "12 cases, 3 repeats each. Better on 5 checks, the same on 3, worse on none. One case regressed inside the tolerance." */
export function gatePassedLine(gate: PromotionGate): string {
  const repeats = gate.repeats ?? 1;
  const better = gate.checksBetter ?? 0;
  const parts = [
    `${countOf(gate.cases ?? 0, "case")}, ${repeats === 1 ? "answered once" : `${repeats} repeats each`}.`,
    `Better on ${better === 0 ? "no checks" : countOf(better, "check")}, the same on ${noneOr(gate.checksSame)}, worse on ${noneOr(gate.checksWorse)}.`,
  ];
  const regressed = gate.regressedCases ?? 0;
  if (regressed > 0) {
    parts.push(`${capitalised(countWord(regressed))} ${regressed === 1 ? "case" : "cases"} regressed inside the tolerance.`);
  }
  parts.push(gateJudgedLine(gate));
  return parts.filter(Boolean).join(" ");
}

/** A run only the judge scores: "10 cases, 3 repeats each. This prompt has no checks, so the judge alone scores it: …" */
export function gateJudgeOnlyLine(gate: PromotionGate): string {
  const repeats = gate.repeats ?? 1;
  const judged = gate.judged;
  return [
    `${countOf(gate.cases ?? 0, "case")}, ${repeats === 1 ? "answered once" : `${repeats} repeats each`}.`,
    judged
      ? `This prompt has no checks, so the judge alone scores it: it prefers the candidate on ${judged.forCandidate} of the ${judged.criteria} judged criteria and the live version on ${judged.forBaseline.length === 0 ? "none" : judged.forBaseline.length}.`
      : "",
  ]
    .filter(Boolean)
    .join(" ");
}

/** Whether the run a put live rests on was scored by the judge alone. */
export function isJudgeOnlyGate(gate: PromotionGate): boolean {
  return gate.judged !== null && (gate.checksBetter ?? 0) + (gate.checksSame ?? 0) + (gate.checksWorse ?? 0) === 0;
}

/** Under "Run R-0039 on the holdout cases gave no result": why, and that nothing shows the version is no worse. */
export function gateNoResultLine(gate: PromotionGate, promptName: string): string {
  return `${promptName} has no checks, and its judged criteria do not count ${whyNotCounting(gate.judged?.state ?? "UNLABELLED")}. Nothing yet shows this version is no worse than the live one.`;
}

/** "Worse than the live version on two checks: score inside the labelled band (75% against 83%) and …" */
export function gateFailedLine(gate: PromotionGate): string {
  const failed = gate.failedChecks;
  const named = failed.map(
    (check) =>
      `${lowerFirst(check.label)} (${percentLabel(check.candidate)} against ${percentLabel(check.baseline)})`,
  );
  const checks =
    failed.length === 0
      ? ""
      : `Worse than the live version on ${countWord(failed.length)} ${failed.length === 1 ? "check" : "checks"}: ${listed(named)}.`;
  return [checks, gateJudgedLine(gate)].filter(Boolean).join(" ");
}

export function countOf(count: number, noun: string): string {
  return `${formatNumber(count)} ${noun}${count === 1 ? "" : "s"}`;
}

/** Small counts in words, as the mockups write them: "two checks", "One case". */
function countWord(count: number): string {
  return SMALL_NUMBERS[count] ?? String(count);
}

function noneOr(count: number | null): string {
  return count ? String(count) : "none";
}

function listed(items: string[]): string {
  if (items.length <= 1) return items.join("");
  return `${items.slice(0, -1).join(", ")} and ${items[items.length - 1]}`;
}

function capitalised(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}
