import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { Link, useParams } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Button, buttonClassName, CardsSkeleton, FormError, Spinner, useToast } from "../../../../components/ui";
import { codeOf, messageFor } from "../../../../lib/errorCodes";
import { useAuth } from "../../../auth/AuthProvider";
import { PlatformTwoFactorRequired } from "../../components/PlatformTwoFactorRequired";
import { usePlatformPageCrumb } from "../../platformCrumb";
import { PROMPT_KEY } from "../../prompts/api/promptApi";
import { PromptPill } from "../../prompts/components/PromptPill";
import { putLivePath } from "../../prompts/lib/promptLabels";
import * as evalApi from "../api/evalApi";
import type { EvalRunDetail } from "../api/types";
import { RunComparison } from "../components/RunComparison";
import { VerdictPill } from "../components/VerdictPill";
import {
  EVAL_RUNS_PATH,
  failureLine,
  isGoing,
  progressLabel,
  runLabel,
  runMeta,
  runTitle,
  STATUS_LABEL,
  timeLeftLabel,
} from "../lib/evalLabels";

const PROGRESS_REFRESH_MS = 2_000;

/** Platform → Evals → one run: its progress while it goes, then what it found. */
export default function EvalRunPage() {
  const { runId = "" } = useParams();
  const { user } = useAuth();
  const toast = useToast();
  const queryClient = useQueryClient();

  const { data: detail, isPending, error } = useQuery({
    queryKey: evalApi.EVAL_RUN_KEY(runId),
    queryFn: ({ signal }) => evalApi.getRun(runId, signal),
    refetchInterval: (query) => (query.state.data && isGoing(query.state.data.run.status) ? PROGRESS_REFRESH_MS : false),
  });

  usePlatformPageCrumb(detail ? runLabel(detail.run.number) : null);

  // A run that ends changes what its prompt's page says about putting the candidate live.
  const status = detail?.run.status;
  const promptId = detail?.run.promptId;
  useEffect(() => {
    if (!status || !promptId || isGoing(status)) return;
    void queryClient.invalidateQueries({ queryKey: PROMPT_KEY(promptId) });
    void queryClient.invalidateQueries({ queryKey: evalApi.EVAL_RUNS_KEY, exact: true });
  }, [status, promptId, queryClient]);

  const cancel = useMutation({
    mutationFn: () => evalApi.cancelRun(runId),
    onSuccess: (updated) => {
      queryClient.setQueryData(evalApi.EVAL_RUN_KEY(runId), updated);
      toast.success(`Run ${runLabel(updated.run.number)} cancelled. The answers it has are kept`);
    },
    onError: (cause) => toast.error(messageFor(cause)),
  });

  if (codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED") return <PlatformTwoFactorRequired />;
  if (isPending) return <CardsSkeleton cards={2} />;
  if (!detail) return <FormError message={error ? messageFor(error) : "This run could not be opened."} />;

  const { run } = detail;
  const canPromote = user?.platformActions.includes("PROMPT_PROMOTE") ?? false;

  return (
    <div>
      <Link to={EVAL_RUNS_PATH} className="mb-3 inline-flex items-center gap-1.5 text-xs font-medium text-u-text3 hover:text-u-text">
        <Icon d={ICONS.back} size={13} />
        Runs
      </Link>

      <div className="mb-4 flex flex-wrap items-start gap-x-4 gap-y-2.5">
        <div className="min-w-0 flex-[1_1_340px]">
          <div className="flex flex-wrap items-center gap-2.5">
            <h1 className="text-title font-semibold">{runTitle(detail)}</h1>
            <VerdictPill verdict={run.verdict} />
            {run.status === "CANCELLED" && <PromptPill tone="plain">Cancelled</PromptPill>}
          </div>
          <div className="mt-1 font-mono text-xs text-u-text3">{runMeta(run)}</div>
        </div>
        {run.status === "FINISHED" && canPromote && !detail.candidate.live && (
          <Link
            to={putLivePath(run.promptId, run.candidateVersion)}
            className={buttonClassName("secondary", "flex-none px-3 py-2 text-body")}
          >
            Put the candidate live…
          </Link>
        )}
      </div>

      {isGoing(run.status) && (
        <RunProgress detail={detail} isCancelling={cancel.isPending} onCancel={() => cancel.mutate()} />
      )}
      {run.status === "CANCELLED" && (
        <div className="rounded-lg border border-u-border bg-u-sunken p-[18px] text-body text-u-text2">
          This run was cancelled after {progressLabel(run)}. A cancelled run gives no result, so it cannot support
          putting a version live.
        </div>
      )}
      {run.status === "FAILED" && <FormError message={failureLine(run.failure)} />}
      {run.status === "FINISHED" && detail.summary && <RunComparison detail={detail} summary={detail.summary} />}
    </div>
  );
}

function RunProgress({
  detail,
  isCancelling,
  onCancel,
}: {
  detail: EvalRunDetail;
  isCancelling: boolean;
  onCancel: () => void;
}) {
  const { run } = detail;
  const made = Math.min(run.callsMade, run.callsPlanned);
  const timeLeft = timeLeftLabel(detail.secondsLeft);
  return (
    <div className="rounded-lg border border-u-border bg-u-sunken p-[18px]">
      <div className="flex flex-wrap items-center gap-3">
        <span className="flex-none text-u-accent">
          <Spinner />
        </span>
        <span className="flex-[1_1_220px] text-body font-medium">
          {run.status === "QUEUED"
            ? STATUS_LABEL.QUEUED
            : `${progressLabel(run)}${timeLeft ? ` · ${timeLeft}` : ""}`}
        </span>
        <Button type="button" size="sm" variant="secondary" loading={isCancelling} onClick={onCancel}>
          Cancel run
        </Button>
      </div>
      <div
        role="progressbar"
        aria-label="Calls made"
        aria-valuemin={0}
        aria-valuemax={run.callsPlanned}
        aria-valuenow={made}
        className="mt-3.5 h-1.5 overflow-hidden rounded-full bg-u-border"
      >
        <div
          className="h-full rounded-full bg-u-accent-solid"
          style={{ width: `${run.callsPlanned === 0 ? 0 : Math.round((made / run.callsPlanned) * 100)}%` }}
        />
      </div>
      <p className="mt-3 font-mono text-xs text-u-text3">
        It keeps going if you leave this page. Only one run goes at a time, so another cannot start until this one
        ends. A cancelled run keeps the answers it has.
      </p>
    </div>
  );
}
