import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { FormError, LinesSkeleton } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { countOf } from "../../../../lib/format";
import * as promptApi from "../../prompts/api/promptApi";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as evalApi from "../api/evalApi";
import type { EvalRunCaseComparison, EvalRunDetail, EvalRunJudged, EvalRunSummary } from "../api/types";
import {
  baselineName,
  changeLabel,
  checkNote,
  checksFootnote,
  evalRubricPath,
  percentLabel,
  scoreLabel,
  SPLIT_LABEL,
  verdictLine,
  whatChanged,
} from "../lib/evalLabels";
import {
  caseJudgedResult,
  judgedFootnote,
  judgedNote,
  judgedStatusLine,
  judgeOrderLine,
  netLabel,
} from "../lib/judgeLabels";

const CHECK_GRID =
  "grid min-w-[760px] grid-cols-[minmax(240px,2fr)_96px_96px_96px_minmax(140px,1.2fr)] items-center gap-3.5 px-4 py-2.5";
const JUDGED_GRID =
  "grid min-w-[820px] grid-cols-[minmax(240px,2fr)_110px_96px_64px_96px_minmax(140px,1.2fr)] items-center gap-3.5 px-4 py-2.5";
const READING_GRID =
  "grid min-w-[760px] grid-cols-[minmax(150px,1fr)_90px_minmax(220px,2fr)_minmax(220px,2fr)] gap-3.5";
const PROSE_GRID = "grid min-w-[760px] grid-cols-[minmax(260px,1fr)_minmax(260px,1fr)] gap-3.5";
const CASE_JUDGED_GRID =
  "grid min-w-[760px] grid-cols-[minmax(200px,1.2fr)_minmax(170px,1fr)_minmax(260px,2fr)] gap-3.5";
const HEADING = "type-micro-label font-mono text-u-text3";

/**
 * What a finished run found: a line on the verdict, each check's pass rate for the two versions, the judged
 * criteria where the dataset has a rubric, then the cases the versions did differently on, regressions first.
 */
export function RunComparison({ detail, summary }: { detail: EvalRunDetail; summary: EvalRunSummary }) {
  const { run } = detail;
  const against = baselineName(detail);
  const baselineHeading = detail.baseline.live ? "Live" : `v${detail.baseline.version}`;
  const hasChecks = summary.checks.length > 0;

  const { data: models } = useQuery({
    queryKey: promptApi.AI_MODELS_KEY,
    queryFn: ({ signal }) => promptApi.listAiModels(signal),
  });
  const modelName = (modelId: string | null) =>
    modelId === null ? "the default model" : (models?.find((model) => model.modelId === modelId)?.displayName ?? modelId);

  const cases = useQuery({
    queryKey: evalApi.EVAL_RUN_CASES_KEY(run.id),
    queryFn: ({ signal }) => evalApi.getRunCases(run.id, signal),
    staleTime: Infinity,
  });
  const splitCases = countOf(run.cases, `${SPLIT_LABEL[run.split].toLowerCase()} case`);

  return (
    <div>
      <div
        role="status"
        className={cn(
          "mb-4 rounded-lg border border-u-border px-3.5 py-[11px] text-body text-u-text",
          run.verdict === "FAIL" ? "bg-u-offlimits-tint" : run.verdict === null ? "bg-u-accent-tint" : "bg-u-direct-tint",
        )}
      >
        {verdictLine(detail)}
      </div>

      {hasChecks ? (
        <>
          <h2 className="mb-2 text-lead font-semibold text-u-text">Checks</h2>
          <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
            <div role="table" aria-label="Checks" className="overflow-x-auto">
              <div role="row" className={cn(CHECK_GRID, "border-b border-u-border-strong bg-u-sunken")}>
                {["Check", "Candidate", baselineHeading, "Change"].map((heading) => (
                  <span key={heading} role="columnheader" className={HEADING}>
                    {heading}
                  </span>
                ))}
                <span role="columnheader" />
              </div>
              {summary.checks.map((check) => (
                <div key={check.id} role="row" className={cn(CHECK_GRID, "border-b border-u-border last:border-b-0")}>
                  <span role="cell" className="text-body text-u-text">
                    {check.label}
                  </span>
                  <span role="cell" className="font-u-num text-body font-medium text-u-text">
                    {percentLabel(check.candidate)}
                  </span>
                  <span role="cell" className="font-u-num text-body text-u-text2">
                    {percentLabel(check.baseline)}
                  </span>
                  <span role="cell" className={cn("font-u-num text-xs font-medium", changeClass(check.change))}>
                    {changeLabel(check.change)}
                  </span>
                  <span role="cell" className="font-mono text-xs text-u-text3">
                    {checkNote(check, summary.tolerancePoints, against)}
                  </span>
                </div>
              ))}
            </div>
          </div>
          <p className="mx-0.5 mt-2.5 font-mono text-xs text-u-text3">{checksFootnote(detail, modelName)}</p>
        </>
      ) : (
        <p className="mx-0.5 font-mono text-xs text-u-text3">
          {run.promptName} has no checks. The judge alone scores it.
        </p>
      )}

      {summary.judged && (
        <JudgedCriteria
          detail={detail}
          judged={summary.judged}
          baselineHeading={baselineHeading}
          footnote={`${judgedFootnote(detail, summary.judged, modelName)}${hasChecks ? "" : ` ${checksFootnote(detail, modelName).replace(/^Share of answers that pass[^.]*\. /, "")}`}`}
        />
      )}

      <h2 className="mb-2 mt-6 text-lead font-semibold text-u-text">Cases that changed</h2>
      {cases.isError ? (
        <FormError message={messageFor(cases.error)} />
      ) : !cases.data ? (
        <LinesSkeleton lines={4} />
      ) : (
        <>
          {cases.data.changed.length > 0 && (
            <div className="rounded-lg border border-u-border bg-u-sunken px-4 py-0.5">
              {cases.data.changed.map((compared) => (
                <ChangedCase
                  key={compared.caseId}
                  compared={compared}
                  against={against}
                  baselineHeading={baselineHeading}
                  repeats={run.repeats}
                />
              ))}
            </div>
          )}
          <p className="mx-0.5 mt-2.5 font-mono text-xs text-u-text3">
            {hasChecks
              ? cases.data.changed.length === 0
                ? `Both versions answered all ${splitCases} the same.`
                : `${cases.data.unchanged} of the ${splitCases} were answered the same by both versions and are not listed.`
              : cases.data.changed.length === 0
                ? `All ${splitCases} were judged a tie on every criterion.`
                : `${cases.data.unchanged} of the ${splitCases} were judged a tie on every criterion and are not listed.`}
          </p>
        </>
      )}
    </div>
  );
}

/**
 * The judged criteria of a run: of its pairs of answers, how many each side won. Always shown; they count toward
 * the result only where the rubric counted when the run finished.
 */
function JudgedCriteria({
  detail,
  judged,
  baselineHeading,
  footnote,
}: {
  detail: EvalRunDetail;
  judged: EvalRunJudged;
  baselineHeading: string;
  footnote: string;
}) {
  return (
    <div>
      <div className="mb-1 mt-6 flex flex-wrap items-center gap-2.5">
        <h2 className="text-lead font-semibold text-u-text">Judged criteria</h2>
        {judged.counted ? <PromptPill tone="live">Counts</PromptPill> : <PromptPill tone="plain">Shown only</PromptPill>}
      </div>
      <p className="mb-2.5 max-w-[760px] text-xs text-u-text2">
        {judgedStatusLine(judged)}{" "}
        <Link to={evalRubricPath(detail.run.datasetId)} className="font-medium text-u-accent hover:underline">
          Open the rubric
        </Link>
      </p>
      <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
        <div role="table" aria-label="Judged criteria" className="overflow-x-auto">
          <div role="row" className={cn(JUDGED_GRID, "border-b border-u-border-strong bg-u-sunken")}>
            {["Criterion", "Candidate better", `${baselineHeading} better`, "Tie", "Net"].map((heading) => (
              <span key={heading} role="columnheader" className={HEADING}>
                {heading}
              </span>
            ))}
            <span role="columnheader" />
          </div>
          {judged.criteria.map((criterion) => (
            <div key={criterion.key} role="row" className={cn(JUDGED_GRID, "border-b border-u-border last:border-b-0")}>
              <span role="cell" className="text-body text-u-text">
                {criterion.name}
              </span>
              <span role="cell" className="font-u-num text-body font-medium text-u-text">
                {criterion.candidateWon}
              </span>
              <span role="cell" className="font-u-num text-body text-u-text2">
                {criterion.baselineWon}
              </span>
              <span role="cell" className="font-u-num text-body text-u-text2">
                {criterion.ties}
              </span>
              <span role="cell" className={cn("font-u-num text-xs font-medium", changeClass(criterion.net))}>
                {netLabel(criterion.net)}
              </span>
              <span role="cell" className="font-mono text-xs text-u-text3">
                {judgedNote(criterion, judged)}
              </span>
            </div>
          ))}
        </div>
      </div>
      <p className="mx-0.5 mt-2.5 font-mono text-xs text-u-text3">{footnote}</p>
    </div>
  );
}

function ChangedCase({
  compared,
  against,
  baselineHeading,
  repeats,
}: {
  compared: EvalRunCaseComparison;
  against: string;
  baselineHeading: string;
  repeats: number;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const fictional = "The case's input is fictional; open it in its dataset.";
  return (
    <div className="border-b border-u-border py-3 last:border-b-0">
      <button
        type="button"
        aria-expanded={isOpen}
        onClick={() => setIsOpen((open) => !open)}
        className="flex w-full flex-wrap items-center gap-2.5 text-left text-u-text"
      >
        {compared.outcome === "REGRESSION" ? (
          <PromptPill tone="danger">Regression</PromptPill>
        ) : (
          <PromptPill tone="live">Better</PromptPill>
        )}
        <span className="font-u-num text-xs text-u-text3">{compared.key}</span>
        <span className="text-body font-semibold">{compared.name}</span>
        <span className="min-w-0 flex-[1_1_260px] text-xs text-u-text2">{whatChanged(compared, against)}</span>
      </button>
      {isOpen && (
        <div className="mt-3 overflow-x-auto">
          {compared.readings.length > 0 ? (
            <>
              <div className={cn(READING_GRID, "pb-2")}>
                {["Competency", "Expected", "Candidate", baselineHeading].map((heading) => (
                  <span key={heading} className={HEADING}>
                    {heading}
                  </span>
                ))}
              </div>
              {compared.readings.map((reading) => (
                <div key={reading.key} className={cn(READING_GRID, "items-start border-t border-u-border py-2.5")}>
                  <span className="text-body font-medium">{reading.label}</span>
                  <span className="font-u-num text-body text-u-text2">{reading.expected}</span>
                  <span>
                    <span
                      className={cn(
                        "font-u-num text-body font-medium",
                        reading.candidate.outside ? "text-u-offlimits" : "text-u-text",
                      )}
                    >
                      {scoreLabel(reading.candidate)}
                    </span>
                    <span className="mt-0.5 block text-xs text-u-text2">{reading.candidate.text}</span>
                  </span>
                  <span>
                    <span className="font-u-num text-body font-medium text-u-text">{scoreLabel(reading.baseline)}</span>
                    <span className="mt-0.5 block text-xs text-u-text2">{reading.baseline.text}</span>
                  </span>
                </div>
              ))}
              <p className="mb-0.5 mt-2 font-mono text-xs text-u-text3">
                {repeats > 1 ? `Scores are the mean of ${repeats} repeats, with their spread. ` : ""}
                {fictional}
              </p>
            </>
          ) : compared.prose ? (
            <>
              <div className={PROSE_GRID}>
                <AnswerAsWritten heading="Candidate" text={compared.prose.candidate} />
                <AnswerAsWritten heading={baselineHeading} text={compared.prose.baseline} />
              </div>
              <p className="mb-0.5 mt-2 font-mono text-xs text-u-text3">
                {repeats > 1 ? `The first of ${repeats} repeats. ` : ""}
                {fictional}
              </p>
            </>
          ) : (
            <>
              {compared.checks.map((check) => (
                <div key={check.id} className="flex flex-wrap gap-x-3.5 border-t border-u-border py-2.5 text-body">
                  <span className="min-w-0 flex-[1_1_260px] font-medium">{check.label}</span>
                  <span className="font-u-num text-u-text">
                    Candidate: {check.candidatePassed} of {check.repeats} pass
                  </span>
                  <span className="font-u-num text-u-text2">
                    {baselineHeading}: {check.baselinePassed} of {check.repeats} pass
                  </span>
                </div>
              ))}
              <p className="mb-0.5 mt-2 font-mono text-xs text-u-text3">{fictional}</p>
            </>
          )}

          {compared.judged.length > 0 && (
            <div className="mt-3.5">
              <div className={cn(CASE_JUDGED_GRID, "pb-2")}>
                {["Judged criterion", "Better", "The judge's reason"].map((heading) => (
                  <span key={heading} className={HEADING}>
                    {heading}
                  </span>
                ))}
              </div>
              {compared.judged.map((row) => (
                <div key={row.key} className={cn(CASE_JUDGED_GRID, "items-start border-t border-u-border py-[9px]")}>
                  <span className="text-body font-medium">{row.name}</span>
                  <span className="text-body text-u-text">{caseJudgedResult(row, baselineHeading)}</span>
                  <span className="text-xs text-u-text2">{row.reason}</span>
                </div>
              ))}
              <p className="mb-0.5 mt-2 font-mono text-xs text-u-text3">
                {judgeOrderLine(compared.candidateShownAs, baselineHeading)}
              </p>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function AnswerAsWritten({ heading, text }: { heading: string; text: string | null }) {
  return (
    <span>
      <span className={HEADING}>{heading}</span>
      <span className="mt-1.5 block rounded-lg border border-u-border bg-u-surface px-3 py-2.5 text-body text-u-text">
        {text ?? "No answer"}
      </span>
    </span>
  );
}

function changeClass(change: number | null): string {
  const rounded = Math.round(change ?? 0);
  return rounded > 0 ? "text-u-direct" : rounded < 0 ? "text-u-offlimits" : "text-u-text3";
}
