import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import * as sourcingApi from "../api/sourcingApi";
import { isSourcingInProgress, type SourcingRun } from "../api/sourcingApi";
import { companiesOf, summaryOf } from "./sourcingSummary";

const POLL_EVERY_MS = 3_000;
const POLL_WHILE_STREAMING_MS = 15_000;

/** Past the server's three-minute deadline, a run still marked running has been lost by its worker. */
const STALE_AFTER_MS = 4 * 60_000;

/**
 * Find executives for one mandate: whether it is offered, the latest run, and the start. The stream
 * refreshes a running run; the slow poll beside it is what lets a lost run time out on screen.
 */
export function useExecutiveSourcing(projectId: string, canWrite: boolean, streamIsLive: boolean) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();

  const config = useQuery({
    queryKey: sourcingApi.SOURCING_CONFIG_KEY,
    queryFn: ({ signal }) => sourcingApi.getSourcingConfig(signal),
    enabled: canWrite,
    staleTime: Infinity,
  });
  const offered = config.data?.enabled === true;

  const latest = useQuery({
    queryKey: sourcingApi.SOURCING_LATEST_KEY(projectId),
    queryFn: ({ signal }) => sourcingApi.getLatestSourcingRun(projectId, signal),
    enabled: canWrite && offered,
    refetchInterval: ({ state: { data } }) => {
      if (!data || !isSourcingInProgress(data) || ageOf(data) > STALE_AFTER_MS) return false;
      return streamIsLive ? POLL_WHILE_STREAMING_MS : POLL_EVERY_MS;
    },
  });
  const run = latest.data ?? null;

  const [dismissedRunId, setDismissedRunId] = useState<string | null>(() => readDismissed(projectId));
  const dismiss = () => {
    if (!run) return;
    setDismissedRunId(run.id);
    writeDismissed(projectId, run.id);
  };

  const seenStatus = useRef<string | null>(null);
  useEffect(() => {
    const previous = seenStatus.current;
    seenStatus.current = run ? `${run.id}:${run.status}` : null;
    if (!run || previous === null || previous === seenStatus.current) return;
    if (!previous.startsWith(`${run.id}:`)) return;
    if (run.status === "COMPLETED") {
      void rowsChanged(projectId);
      toast(summaryOf(run));
    } else if (run.status === "FAILED") {
      void rowsChanged(projectId);
      toast("Find executives stopped before it finished — try again");
    }
  }, [run, projectId, rowsChanged, toast]);

  const start = useMutation({
    mutationFn: (triageCompanyIds: string[]) => sourcingApi.startSourcing(projectId, triageCompanyIds),
    onSuccess: (started) => {
      queryClient.setQueryData(sourcingApi.SOURCING_LATEST_KEY(projectId), started);
      toast(`Finding executives at ${companiesOf(started.companiesTotal)}…`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const isStale = run !== null && isSourcingInProgress(run) && ageOf(run) > STALE_AFTER_MS;
  const isRunning = (isSourcingInProgress(run) && !isStale) || start.isPending;

  return {
    /** False until the deployment says it offers the run — the button is not drawn before. */
    offered,
    config: config.data ?? null,
    /** The latest run, with a lost one read as failed so the button comes back. */
    run: run && isStale ? { ...run, status: "FAILED" as const, error: "Uncava lost track of this run" } : run,
    isRunning,
    isStarting: start.isPending,
    isDismissed: run !== null && run.id === dismissedRunId,
    dismiss,
    start: (triageCompanyIds: string[], onStarted: () => void) =>
      start.mutate(triageCompanyIds, { onSuccess: onStarted }),
  };
}

export type ExecutiveSourcing = ReturnType<typeof useExecutiveSourcing>;

function ageOf(run: SourcingRun): number {
  const since = Date.parse(run.startedAt ?? run.requestedAt);
  return Number.isNaN(since) ? 0 : Date.now() - since;
}

const dismissedKey = (projectId: string) => `lightmove.sourcing.dismissed.${projectId}`;

function readDismissed(projectId: string): string | null {
  try {
    return sessionStorage.getItem(dismissedKey(projectId));
  } catch {
    return null;
  }
}

function writeDismissed(projectId: string, runId: string) {
  try {
    sessionStorage.setItem(dismissedKey(projectId), runId);
  } catch {
    return;
  }
}
