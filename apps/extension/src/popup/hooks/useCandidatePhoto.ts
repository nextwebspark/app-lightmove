import { useQuery } from "@tanstack/react-query";
import { askOrThrow } from "../lib/askOrThrow";

/** The person's stored photo as a data URL, once research has stored one; null for someone without. */
export function useCandidatePhoto(projectId: string, candidateId: string, enrichedAt: string | null) {
  return useQuery<string | null>({
    queryKey: ["extension", "photo", projectId, candidateId, enrichedAt],
    enabled: enrichedAt !== null,
    staleTime: Infinity,
    queryFn: () => askOrThrow({ kind: "candidatePhoto", projectId, candidateId }),
  });
}
