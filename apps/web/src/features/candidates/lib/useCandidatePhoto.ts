import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { ApiRequestError, requestBlob } from "../../../lib/apiClient";
import type { Candidate } from "../api/types";

/**
 * The stored photo as an object URL, or null. Fetched, not `<img src>`, because the endpoint needs the in-memory
 * bearer; `enrichedAt` in the key shows it on the research poll. The object URL is minted and revoked in an effect —
 * one per fetch never revoked pins its Blob for the tab's life.
 */
export function useCandidatePhoto(
  projectId: string,
  candidate: Pick<Candidate, "id" | "enrichedAt"> | null,
): string | null {
  return useStoredPhoto(
    ["candidate-photo", projectId, candidate?.id ?? "none", candidate?.enrichedAt ?? null],
    candidate ? `/projects/${projectId}/candidates/${candidate.id}/photo` : null,
    candidate?.enrichedAt != null,
  );
}

/** The same photo read by person, for the Candidates page — staff-only, outside any one position. */
export function usePersonPhoto(person: { personId: string; enrichedAt: string | null } | null): string | null {
  return useStoredPhoto(
    ["person-photo", person?.personId ?? "none", person?.enrichedAt ?? null],
    person ? `/candidates/${person.personId}/photo` : null,
    person?.enrichedAt != null,
  );
}

function useStoredPhoto(queryKey: readonly unknown[], path: string | null, researched: boolean): string | null {
  const photo = useQuery({
    queryKey,
    queryFn: () => photoOrNothing(path!),
    // Only someone the research has touched can have one; skipping the rest keeps a page of
    // rows from firing a 404 apiece.
    enabled: path !== null && researched,
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

/**
 * A candidate with no stored photo answers 404, which is an answer rather than a failure — so it
 * resolves null instead of throwing. A rejected query is held in an error state, and an errored query
 * ignores `staleTime`: every avatar that remounted asked again for a photo already known not to exist,
 * and the map's tree remounts the lot on every toggle.
 */
async function photoOrNothing(path: string): Promise<Blob | null> {
  try {
    return await requestBlob(path);
  } catch (error) {
    if (error instanceof ApiRequestError && error.code === "NOT_FOUND") {
      return null;
    }
    throw error;
  }
}
