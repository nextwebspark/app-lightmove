import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { PipelineCatalog } from "../../workspace/api/types";

/**
 * A reorder drawn at once and then saved.
 *
 * <p>A refused save is answered by reading the catalog again, never by putting back the picture taken before
 * it: a second reorder may have been drawn since, and the older picture would overwrite it.
 */
export function usePipelineReorder(
  save: (ids: string[]) => Promise<PipelineCatalog>,
  reordered: (catalog: PipelineCatalog, ids: string[]) => PipelineCatalog,
) {
  const queryClient = useQueryClient();
  const toast = useToast();
  // The refusal is handled here, not in mutate's own callbacks: those run for the latest call alone, so an
  // earlier reorder refused while a later one was in flight said nothing.
  const write = useMutation({
    mutationFn: save,
    onSuccess: (catalog) => queryClient.setQueryData(pipelineApi.PIPELINE_CATALOG_KEY, catalog),
    onError: (error) => {
      toast.error(messageFor(error));
      void queryClient.invalidateQueries({ queryKey: pipelineApi.PIPELINE_CATALOG_KEY });
    },
  });

  return (ids: string[]) => {
    void queryClient.cancelQueries({ queryKey: pipelineApi.PIPELINE_CATALOG_KEY });
    queryClient.setQueryData<PipelineCatalog>(
      pipelineApi.PIPELINE_CATALOG_KEY,
      (current) => current && reordered(current, ids),
    );
    write.mutate(ids);
  };
}
