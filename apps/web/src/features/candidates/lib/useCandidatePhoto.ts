import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { requestBlob } from "../../../lib/apiClient";
import type { Candidate } from "../api/types";

/**
 * The stored profile photo as an object URL, or null — no photo and "still being researched" look
 * the same, and the Avatar's initials cover both.
 *
 * <p>Fetched rather than pointed at: the endpoint needs the bearer token, which lives in memory
 * inside apiClient, so a plain `<img src>` would 401. The query key carries `enrichedAt` so the
 * avatar appears on the poll that delivers the research, without anyone reloading.
 *
 * <p>The query caches the Blob and the object URL is minted in an effect, because an object URL is
 * a registration rather than a value: one created per fetch and never revoked pins its Blob for the
 * life of the tab, and this hook runs once per avatar on a grid that refetches all day.
 */
export function useCandidatePhoto(
  projectId: string,
  candidate: Pick<Candidate, "id" | "enrichedAt"> | null,
): string | null {
  const photo = useQuery({
    queryKey: ["candidate-photo", projectId, candidate?.id ?? "none", candidate?.enrichedAt ?? null],
    queryFn: () => requestBlob(`/projects/${projectId}/candidates/${candidate!.id}/photo`),
    // Only a candidate the research has touched can have one; skipping the rest keeps a page of
    // rows from firing a 404 apiece.
    enabled: candidate?.enrichedAt != null,
    // The bytes are immutable per enrichment, and a 404 today is a 404 tomorrow.
    staleTime: Infinity,
    retry: false,
  });

  const blob = photo.data ?? null;
  const [objectUrl, setObjectUrl] = useState<string | null>(null);

  useEffect(() => {
    if (!blob) {
      setObjectUrl(null);
      return;
    }
    const url = URL.createObjectURL(blob);
    setObjectUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [blob]);

  return objectUrl;
}
