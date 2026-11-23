import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, buttonClassName, FormError, Spinner, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { formatNumber } from "../../../../lib/format";
import * as promptApi from "../../prompts/api/promptApi";
import { PromptPill } from "../../prompts/components/PromptPill";
import { promptPath } from "../../prompts/lib/promptLabels";
import * as evalApi from "../api/evalApi";
import type { EvalDatasetDetail, EvalRubricDetail } from "../api/types";
import { evalCalibrationPath } from "../lib/evalLabels";
import { JUDGE_PROMPT_ID, judgeChanged, PICK_LABEL, rubricMeta, rubricText, rubricTitle } from "../lib/judgeLabels";

const CRITERIA_GRID =
  "grid min-w-[700px] grid-cols-[minmax(200px,1.3fr)_minmax(260px,2fr)_110px] gap-3.5 px-4";
const DIFFERENCES_LISTED = 3;

/**
 * A dataset's rubric: what the judge compares two answers on, whether that counts toward a run's result, and how
 * far the judge agrees with a person on each criterion. A rubric ships with its pack and is not edited here.
 */
export function RubricView({ detail }: { detail: EvalDatasetDetail }) {
  const { dataset, rubric } = detail;
  const navigate = useNavigate();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [showsAllDifferences, setShowsAllDifferences] = useState(false);

  const { data: models } = useQuery({
    queryKey: promptApi.AI_MODELS_KEY,
    queryFn: ({ signal }) => promptApi.listAiModels(signal),
    enabled: rubric !== null,
  });

  const startLabelling = useMutation({
    mutationFn: () => evalApi.startLabelling(dataset.id),
    onSuccess: (pairs) => {
      queryClient.setQueryData(evalApi.EVAL_PAIRS_KEY(dataset.id), pairs);
      navigate(evalCalibrationPath(dataset.id));
    },
    onError: (cause) => toast.error(messageFor(cause)),
  });

  const check = useMutation({
    mutationFn: () => evalApi.checkJudge(dataset.id),
    onSuccess: (updated) => queryClient.setQueryData(evalApi.EVAL_DATASET_KEY(dataset.id), updated),
    onError: (cause) => toast.error(messageFor(cause)),
  });

  if (!rubric) {
    return (
      <div className="rounded-lg border border-u-border bg-u-sunken p-[18px] text-body text-u-text2">
        This dataset has no rubric. Its runs are scored by its checks alone.
      </div>
    );
  }

  const canCheck = rubric.state === "UNCHECKED" || rubric.state === "STALE";
  const canReview = rubric.state !== "UNLABELLED" && rubric.state !== "CHECKING";
  const judgeModel = models?.find((model) => model.modelId === rubric.judgeModel)?.displayName ?? rubric.judgeModel;
  const differences = showsAllDifferences ? rubric.differences : rubric.differences.slice(0, DIFFERENCES_LISTED);

  return (
    <div>
      <div className="rounded-lg border border-u-border bg-u-sunken px-[18px] py-4">
        <div className="flex flex-wrap items-start gap-3.5">
          <div className="min-w-0 flex-[1_1_380px]">
            <div className="flex flex-wrap items-center gap-2">
              <StatePill rubric={rubric} />
              <span className="text-sm font-semibold text-u-text">{rubricTitle(rubric)}</span>
            </div>
            <p className="mt-1.5 text-body text-u-text2">{rubricText(rubric)}</p>
            <p className="mt-2 font-mono text-xs text-u-text3">{rubricMeta(rubric)}</p>
          </div>
          <div className="flex flex-wrap gap-2">
            {canCheck && (
              <Button type="button" size="sm" loading={check.isPending} onClick={() => check.mutate()}>
                {rubric.state === "STALE" ? "Check the judge again" : "Check the judge"}
              </Button>
            )}
            {rubric.state === "UNLABELLED" && (
              <Button
                type="button"
                size="sm"
                loading={startLabelling.isPending}
                disabled={!rubric.pairsAvailable}
                onClick={() => startLabelling.mutate()}
              >
                Label pairs
              </Button>
            )}
            {canReview && (
              <Link to={evalCalibrationPath(dataset.id)} className={buttonClassName("secondary", "px-3 py-2 text-body")}>
                Review labels
              </Link>
            )}
          </div>
        </div>
        {canCheck && (
          <p className="mt-2.5 font-mono text-xs text-u-text3">
            Checking makes {formatNumber(rubric.pairsNeeded)} calls, one for each labelled pair, and counts toward the
            AI budget.
          </p>
        )}
        {rubric.lastCheckFailed && rubric.state !== "CHECKING" && (
          <div className="mt-2.5">
            <FormError message="The last check of the judge stopped before it finished. Check it again." />
          </div>
        )}
      </div>

      <div className="mb-2 mt-[22px] flex flex-wrap items-baseline gap-2.5">
        <h2 className="text-sm font-semibold text-u-text">Criteria</h2>
        <span className="font-mono text-xs text-u-text3">Rubric v{rubric.version}</span>
      </div>
      <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
        <div role="table" aria-label="Rubric criteria" className="overflow-x-auto">
          <div role="row" className={cn(CRITERIA_GRID, "items-center border-b border-u-border-strong bg-u-sunken py-2.5")}>
            {[
              "Criterion",
              "The better answer",
              rubric.state === "STALE" && judgeChanged(rubric) ? `Agreement, version ${rubric.checkedJudgeVersion}` : "Agreement",
            ].map((heading) => (
              <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                {heading}
              </span>
            ))}
          </div>
          {rubric.criteria.map((criterion) => (
            <div key={criterion.key} role="row" className={cn(CRITERIA_GRID, "items-start border-b border-u-border py-[11px] last:border-b-0")}>
              <span role="cell" className="text-body font-medium text-u-text">
                {criterion.name}
              </span>
              <span role="cell" className="text-body text-u-text2">
                {criterion.better}
              </span>
              {criterion.agreed === null ? (
                <span role="cell" className="font-mono text-xs text-u-text3">
                  Not measured
                </span>
              ) : (
                <span role="cell" className="font-u-num text-body font-medium text-u-text">
                  {criterion.agreed} of {criterion.of}
                </span>
              )}
            </div>
          ))}
        </div>
      </div>
      <p className="mx-0.5 mt-2.5 max-w-[760px] font-mono text-xs text-u-text3">
        Agreement is the share of labelled pairs on which the judge made the same pick as the person: one answer
        better, the other, or a tie. The judge is the prompt Eval judge
        {rubric.judgeVersion === null ? "" : `, version ${rubric.judgeVersion}`}
        {judgeModel ? `, on ${judgeModel}` : ""}
        {rubric.judgeTemperature === null ? "" : ` at temperature ${formatNumber(rubric.judgeTemperature)}`}.{" "}
        <Link to={promptPath(JUDGE_PROMPT_ID)} className="font-medium text-u-accent hover:underline">
          Open its prompt
        </Link>
      </p>

      {rubric.differences.length > 0 && (
        <>
          <div className="mb-2 mt-[22px] flex flex-wrap items-baseline gap-2.5">
            <h2 className="text-sm font-semibold text-u-text">Where the judge and the person differ</h2>
            <span className="font-mono text-xs text-u-text3">
              {rubric.differences.length} of the {rubric.picks} picks.
              {rubric.differences.length > DIFFERENCES_LISTED && !showsAllDifferences
                ? ` The first ${DIFFERENCES_LISTED} are listed.`
                : ""}
            </span>
            {rubric.differences.length > DIFFERENCES_LISTED && !showsAllDifferences && (
              <button
                type="button"
                onClick={() => setShowsAllDifferences(true)}
                className="text-xs font-medium text-u-accent hover:underline"
              >
                Show all
              </button>
            )}
          </div>
          <div className="rounded-lg border border-u-border bg-u-sunken px-4 py-0.5">
            {differences.map((difference) => (
              <div
                key={`${difference.caseKey}:${difference.criterionKey}`}
                className="flex flex-wrap items-baseline gap-x-3.5 gap-y-1 border-b border-u-border py-[11px] last:border-b-0"
              >
                <span className="w-[58px] flex-none font-u-num text-xs text-u-text3">{difference.caseKey}</span>
                <span className="min-w-0 flex-[1_1_220px] text-body font-medium">{difference.criterion}</span>
                <span className="flex-none text-xs text-u-text2">
                  Person: {PICK_LABEL[difference.person]} · Judge:{" "}
                  {difference.judge === null ? "No pick" : PICK_LABEL[difference.judge]}
                </span>
                {difference.reason && (
                  <span className="flex-[1_1_100%] text-xs text-u-text2">The judge's reason: {difference.reason}</span>
                )}
              </div>
            ))}
          </div>
        </>
      )}
      <p className="mx-0.5 mt-3.5 max-w-[760px] font-mono text-xs text-u-text3">
        A rubric ships with its pack and carries a version. A new version starts with no labels, so its criteria are
        shown only until it is labelled and the judge is checked.
      </p>
    </div>
  );
}

function StatePill({ rubric }: { rubric: EvalRubricDetail }) {
  if (rubric.state === "COUNTS") return <PromptPill tone="live">Counts</PromptPill>;
  if (rubric.state === "STALE") return <PromptPill tone="warning">Check again</PromptPill>;
  if (rubric.state === "CHECKING") {
    return (
      <span className="flex-none text-u-accent">
        <Spinner />
      </span>
    );
  }
  return <PromptPill tone="plain">Shown only</PromptPill>;
}
