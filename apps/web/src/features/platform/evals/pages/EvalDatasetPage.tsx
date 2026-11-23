import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, FormError, LinesSkeleton, useToast } from "../../../../components/ui";
import { cn } from "../../../../lib/cn";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { usePlatformPageCrumb } from "../../platformCrumb";
import { PromptPill } from "../../prompts/components/PromptPill";
import * as evalApi from "../api/evalApi";
import type { EvalCaseSummary, EvalDatasetDetail } from "../api/types";
import { EvalsFrame } from "../components/EvalsFrame";
import { ImportCasesDialog } from "../components/ImportCasesDialog";
import { countOf } from "../../../../lib/format";
import { EVALS_PATH } from "../lib/evalLabels";

const CASE_GRID =
  "grid min-w-[820px] grid-cols-[84px_minmax(220px,2fr)_minmax(100px,.8fr)_96px_minmax(130px,1fr)_84px] items-center gap-3 px-4";

/** Platform → Evals → one dataset: its cases. A case is added or retired, never edited, so a run can name what it scored. */
export default function EvalDatasetPage() {
  const { datasetId = "" } = useParams();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [isImporting, setIsImporting] = useState(false);

  const { data: detail, isPending, error } = useQuery({
    queryKey: evalApi.EVAL_DATASET_KEY(datasetId),
    queryFn: ({ signal }) => evalApi.getDataset(datasetId, signal),
  });

  usePlatformPageCrumb(detail?.dataset.name ?? null);

  const keep = (updated: EvalDatasetDetail) => {
    queryClient.setQueryData(evalApi.EVAL_DATASET_KEY(datasetId), updated);
    void queryClient.invalidateQueries({ queryKey: evalApi.EVAL_DATASETS_KEY, exact: true });
  };

  const retire = useMutation({
    mutationFn: (caseId: string) => evalApi.retireCase(caseId),
    onSuccess: (updated) => {
      keep(updated);
      toast.success("Case retired. Runs that scored it still name it");
    },
    onError: (cause) => toast.error(messageFor(cause)),
  });

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;

  return (
    <EvalsFrame tab="datasets">
      {isPending ? (
        <LinesSkeleton lines={8} />
      ) : !detail ? (
        <FormError message={error ? messageFor(error) : "This dataset could not be opened."} />
      ) : (
        <>
          <div className="mb-3 flex flex-wrap items-center gap-3">
            <Link to={EVALS_PATH} className="inline-flex items-center gap-1.5 text-xs font-medium text-u-text3 hover:text-u-text">
              <Icon d={ICONS.back} size={13} />
              Datasets
            </Link>
            <div className="min-w-0 flex-[1_1_260px]">
              <h1 className="inline text-lead font-semibold text-u-text">{detail.dataset.name}</h1>
              <span className="ml-2 font-mono text-xs text-u-text3">
                {detail.dataset.promptName} · {countOf(detail.dataset.cases, "case")} · {detail.dataset.dev} dev ·{" "}
                {detail.dataset.holdout} holdout
              </span>
            </div>
            {detail.dataset.importable && (
              <Button type="button" size="sm" variant="secondary" onClick={() => setIsImporting(true)}>
                <Icon d={ICONS.importInto} size={14} />
                Import cases
              </Button>
            )}
          </div>

          <div className="overflow-hidden rounded-lg border border-u-border-strong bg-u-surface">
            <div role="table" aria-label="Cases" className="overflow-x-auto">
              <div role="row" className={cn(CASE_GRID, "border-b border-u-border-strong bg-u-sunken py-2.5")}>
                {["Case", "What it tests", "Tag", "Split", "Added by"].map((heading) => (
                  <span key={heading} role="columnheader" className="type-micro-label font-mono text-u-text3">
                    {heading}
                  </span>
                ))}
                <span role="columnheader" />
              </div>
              {detail.cases.map((evalCase) => (
                <CaseRow
                  key={evalCase.id}
                  evalCase={evalCase}
                  isRetiring={retire.isPending && retire.variables === evalCase.id}
                  onRetire={() => retire.mutate(evalCase.id)}
                />
              ))}
              {detail.cases.length === 0 && (
                <div className="px-4 py-10 text-center font-mono text-body text-u-text3">
                  This dataset has no cases yet.
                </div>
              )}
            </div>
          </div>

          {isImporting && (
            <ImportCasesDialog
              dataset={detail.dataset}
              onClose={() => setIsImporting(false)}
              onImported={() => {
                void queryClient.invalidateQueries({ queryKey: evalApi.EVAL_DATASETS_KEY });
              }}
            />
          )}
        </>
      )}
    </EvalsFrame>
  );
}

function CaseRow({
  evalCase,
  isRetiring,
  onRetire,
}: {
  evalCase: EvalCaseSummary;
  isRetiring: boolean;
  onRetire: () => void;
}) {
  const isRetired = evalCase.retiredAt !== null;
  return (
    <div role="row" className={cn(CASE_GRID, "border-b border-u-border py-[11px] last:border-b-0", isRetired && "opacity-55")}>
      <span role="cell" className="font-u-num text-xs text-u-text3">
        {evalCase.key}
      </span>
      <span role="cell" className="truncate text-body font-medium text-u-text">
        {evalCase.name}
      </span>
      <span role="cell" className="truncate text-body text-u-text2">
        {evalCase.tag}
      </span>
      <span role="cell" className="flex">
        {evalCase.split === "HOLDOUT" ? <PromptPill tone="edited">Holdout</PromptPill> : <PromptPill tone="plain">Dev</PromptPill>}
      </span>
      <span role="cell" className="truncate text-body text-u-text2">
        {evalCase.addedBy ?? "Shipped"}
      </span>
      <span role="cell" className="flex justify-end">
        {isRetired ? (
          <span className="font-mono text-xs text-u-text3">Retired</span>
        ) : (
          <Button
            type="button"
            size="xs"
            variant="ghost"
            loading={isRetiring}
            aria-label={`Retire ${evalCase.key}`}
            onClick={onRetire}
          >
            Retire
          </Button>
        )}
      </span>
    </div>
  );
}
