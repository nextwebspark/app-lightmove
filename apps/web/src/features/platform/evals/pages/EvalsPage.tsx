import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, LinesSkeleton, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { formatDate } from "../../../../lib/datetime/format";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as evalApi from "../api/evalApi";
import type { EvalDatasetOverview, EvalRunOverview } from "../api/types";
import { EvalsFrame, type EvalsTab } from "../components/EvalsFrame";
import { StartRunDialog } from "../components/StartRunDialog";
import { VerdictPill } from "../components/VerdictPill";
import {
  costLabel,
  evalDatasetPath,
  evalRunPath,
  isGoing,
  runLabel,
  SPLIT_LABEL,
  startedLabel,
  STATUS_LABEL,
  versionsLabel,
} from "../lib/evalLabels";
import { rubricCellLabel } from "../lib/judgeLabels";

const RUNS_REFRESH_MS = 4_000;

const HEAD = "border-b border-u-border-strong bg-u-sunken py-2.5";
const ROW = "cursor-pointer border-b border-u-border py-3 last:border-b-0 hover:bg-u-sunken";
const DATASET_GRID =
  "grid min-w-[980px] grid-cols-[minmax(220px,2fr)_minmax(140px,1.1fr)_70px_minmax(150px,1.1fr)_minmax(130px,.9fr)_96px_minmax(110px,.9fr)] items-center gap-3.5 px-4";
const RUN_GRID =
  "grid min-w-[1020px] grid-cols-[78px_minmax(160px,1.4fr)_minmax(150px,1.1fr)_84px_minmax(170px,1.3fr)_minmax(170px,1.3fr)_minmax(150px,1.1fr)] items-center gap-3.5 px-4";

/** Platform → Evals: the datasets a prompt version is scored on, and the runs that scored one. */
export default function EvalsPage() {
  const navigate = useNavigate();
  const toast = useToast();
  const [params] = useSearchParams();
  const tab: EvalsTab = params.get("tab") === "runs" ? "runs" : "datasets";
  const [isStarting, setIsStarting] = useState(false);

  const datasets = useQuery({
    queryKey: evalApi.EVAL_DATASETS_KEY,
    queryFn: ({ signal }) => evalApi.listDatasets(signal),
  });
  const runs = useQuery({
    queryKey: evalApi.EVAL_RUNS_KEY,
    queryFn: ({ signal }) => evalApi.listRuns(signal),
    enabled: tab === "runs",
    refetchInterval: (query) => (query.state.data?.some((run) => isGoing(run.status)) ? RUNS_REFRESH_MS : false),
  });

  if (codeOf(datasets.error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;

  return (
    <EvalsFrame
      tab={tab}
      action={
        tab === "runs" && (
          <Button
            type="button"
            size="sm"
            className="whitespace-nowrap"
            disabled={!datasets.data}
            onClick={() => setIsStarting(true)}
          >
            <Icon d={ICONS.plus} size={14} />
            Start a run
          </Button>
        )
      }
    >
      {tab === "datasets" &&
        (datasets.isPending ? (
          <LinesSkeleton lines={5} />
        ) : datasets.isError ? (
          <FormError message={messageFor(datasets.error)} />
        ) : (
          <DatasetTable datasets={datasets.data} onOpen={(dataset) => navigate(evalDatasetPath(dataset.id))} />
        ))}

      {tab === "runs" &&
        (runs.isPending ? (
          <LinesSkeleton lines={5} />
        ) : runs.isError ? (
          <FormError message={messageFor(runs.error)} />
        ) : (
          <RunTable runs={runs.data} onOpen={(run) => navigate(evalRunPath(run.id))} />
        ))}

      {isStarting && datasets.data && (
        <StartRunDialog
          datasets={datasets.data}
          onClose={() => setIsStarting(false)}
          onStarted={(started) => {
            toast.success(`Run ${runLabel(started.run.number)} started`);
            navigate(evalRunPath(started.run.id));
          }}
        />
      )}
    </EvalsFrame>
  );
}

function DatasetTable({
  datasets,
  onOpen,
}: {
  datasets: EvalDatasetOverview[];
  onOpen: (dataset: EvalDatasetOverview) => void;
}) {
  return (
    <>
      <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
        <div role="table" aria-label="Datasets" className="overflow-x-auto">
          <div role="row" className={cn(DATASET_GRID, HEAD)}>
            {["Dataset", "Prompt", "Cases", "Split", "Rubric", "Source", "Updated"].map((heading) => (
              <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                {heading}
              </span>
            ))}
          </div>
          {datasets.map((dataset) => (
            // A mouse convenience only: the dataset's name is the row's keyboard-reachable way in.
            <div key={dataset.id} role="row" onClick={() => onOpen(dataset)} className={cn(DATASET_GRID, ROW)}>
              <span role="cell" className="truncate">
                <Link
                  to={evalDatasetPath(dataset.id)}
                  // The row would navigate again on the bubbled click, pushing a second history entry.
                  onClick={(event) => event.stopPropagation()}
                  className="text-body font-semibold text-u-text hover:underline"
                >
                  {dataset.name}
                </Link>
              </span>
              <span role="cell" className="truncate text-body text-u-text2">
                {dataset.promptName}
              </span>
              <span role="cell" className="font-u-num text-body font-medium text-u-text">
                {dataset.cases}
              </span>
              <span role="cell" className="truncate text-body text-u-text2">
                {dataset.dev} dev · {dataset.holdout} holdout
              </span>
              <span role="cell" className={cn("truncate text-body", dataset.rubric ? "text-u-text2" : "text-u-text3")}>
                {rubricCellLabel(dataset.rubric)}
              </span>
              <span role="cell">
                <PromptPill tone="plain">Synthetic</PromptPill>
              </span>
              <span role="cell" className="truncate text-body text-u-text2">
                {formatDate(dataset.updatedAt)}
              </span>
            </div>
          ))}
        </div>
      </div>
      <p className="mx-0.5 mt-3 max-w-[700px] font-mono text-xs text-u-text3">
        Dev cases are for working on a prompt. Holdout cases are kept back: the check before a version goes live
        scores on them, so a prompt cannot be tuned to pass it. A rubric's criteria are judged on every run, and count
        only while the judge agrees with a person's labels.
      </p>
    </>
  );
}

function RunTable({ runs, onOpen }: { runs: EvalRunOverview[]; onOpen: (run: EvalRunOverview) => void }) {
  return (
    <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
      <div role="table" aria-label="Runs" className="overflow-x-auto">
        <div role="row" className={cn(RUN_GRID, HEAD)}>
          {["Run", "Prompt", "Versions", "Split", "Result", "Cost", "Started"].map((heading) => (
            <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
              {heading}
            </span>
          ))}
        </div>
        {runs.map((run) => (
          <div key={run.id} role="row" onClick={() => onOpen(run)} className={cn(RUN_GRID, ROW)}>
            <span role="cell">
              <Link
                to={evalRunPath(run.id)}
                onClick={(event) => event.stopPropagation()}
                aria-label={`Open run ${runLabel(run.number)}`}
                className="font-u-num text-xs text-u-text3 hover:text-u-text hover:underline"
              >
                {runLabel(run.number)}
              </Link>
            </span>
            <span role="cell" className="truncate text-body font-semibold text-u-text">
              {run.promptName}
            </span>
            <span role="cell" className="truncate text-body text-u-text2">
              {versionsLabel(run)}
            </span>
            <span role="cell" className="truncate text-body text-u-text2">
              {SPLIT_LABEL[run.split]}
            </span>
            <span role="cell" className="flex flex-wrap items-center gap-2">
              <span className="text-body text-u-text2">{STATUS_LABEL[run.status]}</span>
              <VerdictPill verdict={run.verdict} status={run.status} />
            </span>
            <span role="cell" className="truncate text-body text-u-text2">
              {costLabel(run)}
            </span>
            <span role="cell" className="truncate text-body text-u-text2">
              {startedLabel(run)}
            </span>
          </div>
        ))}
        {runs.length === 0 && (
          <div className="px-4 py-10 text-center font-mono text-body text-u-text3">
            No runs yet. Start one to score a version against the live one.
          </div>
        )}
      </div>
    </div>
  );
}
