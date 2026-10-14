import { useMutation, useQueryClient, type QueryClient, type QueryKey } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelinePage, PipelineRankMove, PipelineRow } from "../api/types";

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

/** A card dropped on a stage: the existing status write, with that stage's default status. */
export function useStageDrop(projectId: string) {
  const toast = useToast();
  const rowsChanged = useProjectRowsChanged();
  return useMutation({
    mutationFn: ({ candidateId, status }: { candidateId: string; status: string; stageLabel: string }) =>
      candidatesApi.changeCandidateStatus(projectId, candidateId, status),
    onSuccess: (saved, { stageLabel }) => {
      toast(`${saved.fullName} moved to ${stageLabel} · ${saved.statusLabel ?? saved.status}`);
      void rowsChanged(projectId);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
}
