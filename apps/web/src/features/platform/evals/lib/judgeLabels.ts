import { formatDate } from "../../../../lib/datetime/format";
import { formatNumber, joinNames } from "../../../../lib/format";
import type { PromotionGate } from "../../prompts/api/types";
import type {
  EvalCaseJudgedCriterion,
  EvalRubricDetail,
  EvalRubricSummary,
  EvalRunDetail,
  EvalRunJudged,
  JudgedCriterionScore,
  JudgePick,
  RubricState,
} from "../api/types";

/** The server's rule (`JudgeCalibrationService`), for the sentences that name it where no rubric is in hand. */
export const JUDGE_THRESHOLD_PERCENT = 80;
export const JUDGE_PAIRS = 20;

export const JUDGE_PROMPT_ID = "eval-judge";

export const PICK_LABEL: Record<JudgePick, string> = { A: "A better", TIE: "Tie", B: "B better" };

/** "v1 · counts", "v1 · shown only", or "None" for a dataset no judge scores. */
export function rubricCellLabel(rubric: EvalRubricSummary | null): string {
  if (!rubric) return "None";
  return `v${rubric.version} · ${rubric.state === "COUNTS" ? "counts" : "shown only"}`;
}

/** Ends "its judged criteria do not count …": why, and so what would change it. */
export function whyNotCounting(state: RubricState | string): string {
  switch (state) {
    case "SHOWN_ONLY":
      return `while the judge agrees with a person on less than ${JUDGE_THRESHOLD_PERCENT}% of the labelled picks`;
    case "STALE":
      return "until the judge is checked again on the labelled pairs";
    case "UNCHECKED":
      return "until the judge has been checked against the labelled pairs";
    case "CHECKING":
      return "until the check of the judge finishes";
    default:
      return `until a person has labelled ${JUDGE_PAIRS} pairs and the judge has been checked against them`;
  }
}

export function rubricTitle(rubric: EvalRubricDetail): string {
  switch (rubric.state) {
    case "COUNTS":
      return "The judged criteria count";
    case "CHECKING":
      return "Checking the judge against your labels";
    case "STALE":
      return judgeChanged(rubric)
        ? "The judge changed, so the judged criteria are shown only"
        : "A label changed, so the judged criteria are shown only";
    default:
      return "The judged criteria are shown only";
  }
}

export function rubricText(rubric: EvalRubricDetail): string {
  const { thresholdPercent: threshold, pairsNeeded: pairs } = rubric;
  const agreement = `${rubric.agreed} of ${rubric.picks} picks (${percentOf(rubric.agreementPercent)})`;
  switch (rubric.state) {
    case "COUNTS":
      return `The judge agrees with a person on ${agreement}. At ${threshold}% or more, a run's judged criteria count toward its result, and so toward putting a version live.`;
    case "SHOWN_ONLY":
      return `The judge agrees with a person on ${agreement}, below the ${threshold}% needed. Runs show the judged criteria, and they never count toward a result or toward putting a version live.`;
    case "STALE":
      return judgeChanged(rubric)
        ? `Agreement was ${percentOf(rubric.agreementPercent)} with version ${rubric.checkedJudgeVersion} of the judge. Version ${rubric.judgeVersion} is live now. Check it again on the ${pairs} labelled pairs: nothing needs labelling again.`
        : `Agreement was ${percentOf(rubric.agreementPercent)}, and a label has changed since. Check the judge again on the ${pairs} labelled pairs.`;
    case "CHECKING":
      return `${rubric.checkJudged ?? 0} of ${pairs} pairs judged. It keeps going if you leave this page.`;
    case "UNCHECKED":
      return `All ${pairs} pairs are labelled. Check the judge against them: until it agrees on ${threshold}% of the picks, runs show the judged criteria and they never count.`;
    default:
      return `${rubric.pairsLabelled === 0 ? "No pairs are labelled yet" : `${rubric.pairsLabelled} of ${pairs} pairs are labelled`}. Until a person has labelled ${pairs} pairs and the judge agrees on ${threshold}% of the picks, runs show the judged criteria and they never count.`;
  }
}

/** Who labelled and when the judge was last checked; before any label, where the pairs come from. */
export function rubricMeta(rubric: EvalRubricDetail): string {
  if (rubric.pairsLabelled === 0 || !rubric.labelledAt) {
    const source = "The pairs are answers two versions gave to the same dev case in a finished run.";
    return rubric.pairsAvailable ? source : `${source} With no finished run on the dev cases, start one first.`;
  }
  const labelled = `${countOfPairs(rubric.pairsLabelled)} labelled${rubric.labelledBy ? ` by ${rubric.labelledBy}` : ""}, ${formatDate(rubric.labelledAt)}`;
  if (!rubric.checkedAt || rubric.state === "CHECKING") return labelled;
  return `${labelled} · judge last checked ${formatDate(rubric.checkedAt)}, as version ${rubric.checkedJudgeVersion}`;
}

/** The judge's live version is not the one the labels were checked against. */
export function judgeChanged(rubric: EvalRubricDetail): boolean {
  return rubric.checkedJudgeVersion !== null && rubric.checkedJudgeVersion !== rubric.judgeVersion;
}

/** "+12 pts", "−8 pts", "Same"; empty when no pair was judged. */
export function netLabel(net: number | null): string {
  if (net === null) return "";
  const rounded = Math.round(net);
  if (rounded === 0) return "Same";
  return `${rounded > 0 ? "+" : "−"}${formatNumber(Math.abs(rounded))} pts`;
}

export function judgedNote(score: JudgedCriterionScore, judged: EvalRunJudged): string {
  if (score.worseBeyondTolerance && judged.counted) {
    return `Outside the tolerance of ${formatNumber(judged.tolerancePoints)} points`;
  }
  return score.unjudged > 0 ? `${score.unjudged} not judged` : "";
}

/** Under the Judged criteria heading: what the judge's agreement was when the run finished, and so whether they count. */
export function judgedStatusLine(judged: EvalRunJudged): string {
  const shownOnly = "These criteria are shown and do not count toward the result.";
  if (judged.counted) {
    return `When this run finished, the judge agreed with a person on ${percentOf(judged.agreementPercent)} of the labelled picks, so these criteria count toward the result.`;
  }
  if (judged.state === "SHOWN_ONLY") {
    return `When this run finished, the judge agreed with a person on ${percentOf(judged.agreementPercent)} of the labelled picks, below the ${JUDGE_THRESHOLD_PERCENT}% needed. ${shownOnly}`;
  }
  if (judged.state === "STALE") {
    return `When this run finished, the judge or a label had changed since its agreement with a person was measured. ${shownOnly}`;
  }
  return `When this run finished, the judge's agreement with a person had not been measured. ${shownOnly}`;
}

/** Under the judged table: what a pair is, how net is taken, and which judge and rubric made the picks. */
export function judgedFootnote(detail: EvalRunDetail, judged: EvalRunJudged, modelName: (modelId: string | null) => string): string {
  const against = detail.baseline.live ? "the live version" : `v${detail.baseline.version}`;
  const judge = `Eval judge${judged.judgeVersion === null ? "" : ` v${judged.judgeVersion}`} on ${modelName(judged.judgeModel)}, temperature 0 · rubric v${judged.rubricVersion}.`;
  return `${countOfPairs(judged.pairs)}: each answer of the candidate beside ${against}'s answer to the same case and repeat. The judge sees the two in a random order, which is recorded, and picks the better on each criterion, or a tie. Net is the share of pairs the candidate won less the share ${against} won. Past ${formatNumber(judged.tolerancePoints)} points against the candidate, a criterion is worse. ${judge}`;
}

/** "Candidate better · 3 of 3 pairs", "Live better · 2 of 3 pairs", "Tie · 3 of 3 pairs". */
export function caseJudgedResult(row: EvalCaseJudgedCriterion, baselineHeading: string): string {
  const side = row.result === "CANDIDATE" ? "Candidate better" : row.result === "BASELINE" ? `${baselineHeading} better` : "Tie";
  return `${side} · ${row.pairs} of ${countOfPairs(row.of)}`;
}

/** "Order shown to the judge, recorded with each pair: pair 1, live as A and candidate as B · …" */
export function judgeOrderLine(candidateShownAs: ("A" | "B")[], baselineHeading: string): string {
  const other = baselineHeading.toLowerCase() === "live" ? "live" : baselineHeading;
  const order = candidateShownAs
    .map((shownAs, index) =>
      shownAs === "A" ? `pair ${index + 1}, candidate as A and ${other} as B` : `pair ${index + 1}, ${other} as A and candidate as B`,
    )
    .join(" · ");
  return `Order shown to the judge, recorded with each pair: ${order}. Each reason is the judge's own, on the first pair it gave one for.`;
}

/** One sentence on what the judge made of a case, for a prompt it alone scores. */
export function judgedChange(judged: EvalCaseJudgedCriterion[], against: string): string {
  const decided = judged.find((row) => row.result === "BASELINE") ?? judged.find((row) => row.result === "CANDIDATE");
  if (!decided) return "";
  const side = decided.result === "CANDIDATE" ? "the candidate" : against;
  return `${decided.name}: the judge preferred ${side} on ${decided.pairs} of ${countOfPairs(decided.of)}.`;
}

/** What the judge adds to the check at put live, after the checks' own sentence. Empty when no judge scored the run. */
export function gateJudgedLine(gate: PromotionGate): string {
  const judged = gate.judged;
  if (!judged) return "";
  if (!judged.counted) return "The judged criteria are shown on the run and do not count.";
  if (gate.state === "FAILED") {
    if (judged.forBaseline.length === 0) return "";
    const named = judged.forBaseline.map(lowerFirst);
    return `The judge prefers the live version on ${named.length === 1 ? "one criterion" : `${named.length} criteria`}: ${joinNames(named)}.`;
  }
  return `The judge prefers the candidate on ${judged.forCandidate} of the ${judged.criteria} judged criteria and the live version on ${judged.forBaseline.length === 0 ? "none" : judged.forBaseline.length}.`;
}

/** "36 calls by the judge, one for each pair of answers." Empty for a dataset with no rubric. */
export function judgeCallsLine(judgeCalls: number): string {
  return judgeCalls > 0 ? `${formatNumber(judgeCalls)} of the calls are the judge's, one for each pair of answers.` : "";
}

function countOfPairs(pairs: number): string {
  return `${formatNumber(pairs)} ${pairs === 1 ? "pair" : "pairs"}`;
}

function percentOf(percent: number | null): string {
  return percent === null ? "none" : `${formatNumber(Math.round(percent))}%`;
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1);
}
