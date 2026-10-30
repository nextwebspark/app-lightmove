import { useQuery } from "@tanstack/react-query";
import type { PipelineCatalog } from "../../api/types";
import { askOrThrow } from "../lib/askOrThrow";

/** The workspace's own stages and statuses — the paired workspace's, read once a session and kept a few minutes. */
export function usePipelineCatalog() {
  return useQuery<PipelineCatalog>({
    queryKey: ["extension", "pipeline"],
    queryFn: () => askOrThrow({ kind: "readPipeline" }),
    staleTime: 5 * 60_000,
  });
}
