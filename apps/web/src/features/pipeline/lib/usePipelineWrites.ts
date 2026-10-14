import { useMutation, useQueryClient, type QueryClient, type QueryKey } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelinePage, PipelineRankMove, PipelineRow, PipelineStageCount } from "../api/types";
import type { BoardPlacement } from "./pipelineView";

type Snapshot = [QueryKey, PipelinePage | undefined][];

/** One row changed in every cached read of the position's pipeline; the snapshot puts them back. */
function patchRow(
  queryClient: QueryClient,
  projectId: string,
  candidateId: string,
  patch: (row: PipelineRow) => PipelineRow,
): Snapshot {
  const snapshot = queryClient.getQueriesData<PipelinePage>({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(projectId) });
  for (const [key, page] of snapshot) {
    if (!page?.rows) continue;
    queryClient.setQueryData<PipelinePage>(key, {
      ...page,
      rows: page.rows.map((row) => (row.candidateId === candidateId ? patch(row) : row)),
    });
  }
  return snapshot;
}

function restore(queryClient: QueryClient, snapshot: Snapshot) {
  for (const [key, page] of snapshot) queryClient.setQueryData(key, page);
}

/** Shown to the client or kept internal, flipped at once and put back if the server refuses it. */
export function useClientVisibility(projectId: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: ({ candidateId, clientVisible }: { candidateId: string; clientVisible: boolean }) =>
      pipelineApi.setClientVisibility(projectId, candidateId, clientVisible),
    onMutate: async ({ candidateId, clientVisible }) => {
      await queryClient.cancelQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(projectId) });
      return patchRow(queryClient, projectId, candidateId, (row) => ({ ...row, clientVisible }));
    },
    onError: (error, _variables, snapshot) => {
      if (snapshot) restore(queryClient, snapshot);
      toast.error(messageFor(error));
    },
    onSuccess: (saved) => {
      patchRow(queryClient, projectId, saved.candidateId, () => saved);
    },
  });
}

export function useNextAction(projectId: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: ({ candidateId, text, dueAt }: { candidateId: string; text: string | null; dueAt: string | null }) =>
      pipelineApi.setNextAction(projectId, candidateId, { text, dueAt }),
    onSuccess: (saved) => {
      patchRow(queryClient, projectId, saved.candidateId, () => saved);
      toast(saved.nextAction ? "Next action saved" : "Next action cleared");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
}

/** A row dragged within its stage: the list shows the new order at once, and the server keeps it. */
export function useRankMove(projectId: string, queryKey: QueryKey) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: ({ candidateId, move }: { candidateId: string; move: PipelineRankMove; rows: PipelineRow[] }) =>
      pipelineApi.movePipelineRank(projectId, candidateId, move),
    onMutate: async ({ rows }) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData<PipelinePage>(queryKey);
      if (previous) queryClient.setQueryData<PipelinePage>(queryKey, { ...previous, rows });
      return previous;
    },
    onError: (error, _variables, previous) => {
      if (previous) queryClient.setQueryData(queryKey, previous);
      toast.error(messageFor(error));
    },
    onSettled: () => void queryClient.invalidateQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(projectId) }),
  });
}

/**
 * A card dropped on the board: the status write when it changes stage, then the rank write that puts it where it
 * was dropped, as one mutation so no refetch lands between them. The board shows the new order at once.
 */
export function useBoardMove(projectId: string, queryKey: QueryKey) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const rowsChanged = useProjectRowsChanged();
  return useMutation({
    mutationFn: async (placement: BoardPlacement) => {
      const saved = placement.status
        ? await candidatesApi.changeCandidateStatus(projectId, placement.candidateId, placement.status)
        : null;
      if (placement.move) {
        try {
          await pipelineApi.movePipelineRank(projectId, placement.candidateId, placement.move);
        } catch (error) {
          if (!saved) throw error;
          toast.error(messageFor(error));
        }
      }
      return saved;
    },
    onMutate: async (placement) => {
      await queryClient.cancelQueries({ queryKey: pipelineApi.PIPELINE_KEY_PREFIX(projectId) });
      const previous = queryClient.getQueryData<PipelinePage>(queryKey);
      if (previous) {
        queryClient.setQueryData<PipelinePage>(queryKey, {
          ...previous,
          rows: placement.rows,
          stageCounts: recounted(previous.stageCounts, placement.fromStageKey, placement.stageKey),
        });
      }
      return previous;
    },
    onError: (error, _placement, previous) => {
      if (previous) queryClient.setQueryData(queryKey, previous);
      toast.error(messageFor(error));
    },
    onSuccess: (saved, { stageLabel }) => {
      if (saved) toast(`${saved.fullName} moved to ${stageLabel} · ${saved.statusLabel ?? saved.status}`);
    },
    onSettled: () => void rowsChanged(projectId),
  });
}

function recounted(stageCounts: PipelineStageCount[], from: string, to: string): PipelineStageCount[] {
  if (from === to) return stageCounts;
  return stageCounts.map((stage) =>
    stage.key === from ? { ...stage, count: stage.count - 1 } : stage.key === to ? { ...stage, count: stage.count + 1 } : stage,
  );
}
