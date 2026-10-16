import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useCallback } from "react";
import { LEGACY_CATALOG, statusStyle, type HeldStatus } from "../../candidates/lib/pipelineStatus";
import * as pipelineApi from "../api/pipelineApi";
import type { PipelineCatalog } from "../api/types";

/**
 * The workspace's stages and statuses. `catalog` is never empty: until the read lands, or if it is refused,
 * it is the seven built-in statuses, which every workspace holds and none can retire.
 */
export function usePipelineCatalog() {
  const query = useQuery({
    queryKey: pipelineApi.PIPELINE_CATALOG_KEY,
    queryFn: ({ signal }) => pipelineApi.pipelineCatalog(signal),
    staleTime: 5 * 60_000,
  });
  return { catalog: query.data ?? LEGACY_CATALOG, isLoaded: query.isSuccess, query };
}

/** `statusStyle` bound to the workspace's catalog. */
export function useStatusStyle() {
  const { catalog } = usePipelineCatalog();
  return useCallback((key: string, held?: HeldStatus | null) => statusStyle(key, held, catalog), [catalog]);
}

/** An admin's write to the catalog: every one answers the whole catalog, which becomes the cached read. */
export function usePipelineWrite<TArgs>(write: (args: TArgs) => Promise<PipelineCatalog>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: write,
    onSuccess: (catalog) => {
      queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, catalog);
      // A retired status can break an automation; the card reads that from the server.
      void queryClient.invalidateQueries({ queryKey: pipelineApi.STATUS_AUTOMATIONS_KEY });
    },
  });
}
