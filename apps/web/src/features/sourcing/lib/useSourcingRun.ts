import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import * as sourcingApi from "../api/sourcingApi";
import { isInProgress, type SourcingRun } from "../api/sourcingApi";
import { companiesOf, peopleOf } from "./sourcingSummary";

const POLL_EVERY_MS = 3_000;
const POLL_WHILE_STREAMING_MS = 15_000;

/** What the toolbar button says about the position's latest search. */
export type FindButtonState =
  | { kind: "idle" }
  | { kind: "running"; progress: string }
  | { kind: "ready"; count: number }
  | { kind: "failed" };

/**
 * Find executives for one position: whether it is offered, the latest search, its start and stop, and what the
 * toolbar button shows. The stream refreshes a running search; the slow poll beside it lets a lost run time out on
 * screen. A finished search is announced once, and its count stays on the button until the panel is opened.
 */
export function useSourcingRun(projectId: string, enabled: boolean, streamIsLive: boolean) {
  const toast = useToast();
  const queryClient = useQueryClient();

  const config = useQuery({
    queryKey: sourcingApi.SOURCING_CONFIG_KEY,
    queryFn: ({ signal }) => sourcingApi.getSourcingConfig(signal),
    enabled,
    staleTime: Infinity,
  });
  const offered = config.data?.enabled === true;
  const lostAfterMs = (config.data?.lostAfterSeconds ?? 0) * 1000;

  const latest = useQuery({
    queryKey: sourcingApi.sourcingKeys.latest(projectId),
    queryFn: ({ signal }) => sourcingApi.getLatestRun(projectId, signal),
    enabled: enabled && offered,
    refetchInterval: ({ state: { data } }) => {
      if (!data || !isInProgress(data) || isLost(data, lostAfterMs)) return false;
      return streamIsLive ? POLL_WHILE_STREAMING_MS : POLL_EVERY_MS;
    },
  });
  const latestRun = latest.data ?? null;
  const isStale = latestRun !== null && isInProgress(latestRun) && isLost(latestRun, lostAfterMs);
  const run = useMemo<SourcingRun | null>(
    () =>
      latestRun && isStale ? { ...latestRun, status: "FAILED", error: "Uncava lost track of this search" } : latestRun,
    [latestRun, isStale],
  );

  const [openedRunId, setOpenedRunId] = useState<string | null>(() => readOpened(projectId));
  const runId = run?.id ?? null;
  const markOpened = useCallback(() => {
    if (!runId) return;
    setOpenedRunId(runId);
    writeOpened(projectId, runId);
  }, [projectId, runId]);

  const seenStatus = useRef<string | null>(null);
  useEffect(() => {
    const previous = seenStatus.current;
    seenStatus.current = run ? `${run.id}:${run.status}` : null;
    if (!run || previous === null || previous === seenStatus.current || !previous.startsWith(`${run.id}:`)) return;
    if (run.status === "COMPLETED") {
      void queryClient.invalidateQueries({ queryKey: sourcingApi.sourcingKeys.suggestions(projectId) });
      toast(
        run.suggested > 0
          ? `${peopleOf(run.suggested)} found at ${companiesOf(run.companiesTotal)} — open Find executives to review`
          : `Nobody who fits was found at ${companiesOf(run.companiesTotal)}`,
      );
    } else if (run.status === "FAILED" || run.status === "STOPPED") {
      void queryClient.invalidateQueries({ queryKey: sourcingApi.sourcingKeys.suggestions(projectId) });
      toast(`Search stopped — ${peopleOf(run.suggested)} found before it did`);
    }
  }, [run, projectId, queryClient, toast]);

  const start = useMutation({
    mutationFn: (triageCompanyIds: readonly string[]) => sourcingApi.startSearch(projectId, triageCompanyIds),
    onSuccess: (started) => {
      queryClient.setQueryData(sourcingApi.sourcingKeys.latest(projectId), started);
      setOpenedRunId(started.id);
      writeOpened(projectId, started.id);
    },
    onError: (error) => {
      if (!isBillingRefusal(error)) toast.error(messageFor(error));
    },
  });

  const stop = useMutation({
    mutationFn: (runId: string) => sourcingApi.stopSearch(projectId, runId),
    onSuccess: (stopped) => queryClient.setQueryData(sourcingApi.sourcingKeys.latest(projectId), stopped),
    onError: (error) => toast.error(messageFor(error)),
  });

  const isRunning = (isInProgress(run) && !isStale) || start.isPending;
  const unopened = run !== null && run.id !== openedRunId;
  const button: FindButtonState = isRunning
    ? { kind: "running", progress: run ? `${run.companiesDone}/${run.companiesTotal}` : "" }
    : unopened && run?.status === "COMPLETED" && run.suggested > 0
      ? { kind: "ready", count: run.suggested }
      : unopened && (run?.status === "FAILED" || run?.status === "STOPPED")
        ? { kind: "failed" }
        : { kind: "idle" };

  return {
    /** False until the deployment says it offers the search — the button is not drawn before. */
    offered,
    config: config.data ?? null,
    run,
    isRunning,
    button,
    markOpened,
    start: (triageCompanyIds: readonly string[]) => start.mutate(triageCompanyIds),
    isStarting: start.isPending,
    stop: () => run && stop.mutate(run.id),
    isStopping: stop.isPending || run?.stopRequested === true,
  };
}

export type SourcingRunState = ReturnType<typeof useSourcingRun>;

function isLost(run: SourcingRun, lostAfterMs: number): boolean {
  const since = Date.parse(run.startedAt ?? run.requestedAt);
  return lostAfterMs > 0 && !Number.isNaN(since) && Date.now() - since > lostAfterMs;
}

const openedKey = (projectId: string) => `lightmove.sourcing.opened.${projectId}`;

function readOpened(projectId: string): string | null {
  try {
    return localStorage.getItem(openedKey(projectId));
  } catch {
    return null;
  }
}

function writeOpened(projectId: string, runId: string) {
  try {
    localStorage.setItem(openedKey(projectId), runId);
  } catch {
    return;
  }
}
