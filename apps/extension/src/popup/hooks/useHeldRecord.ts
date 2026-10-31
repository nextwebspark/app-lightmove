import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { HeldCandidate, TriagedCompany } from "../../api/types";
import { askOrThrow } from "../lib/askOrThrow";

export const heldPersonKey = (projectId: string | null, linkedinUrl: string | null) =>
  ["extension", "held", "person", projectId, linkedinUrl] as const;

/** Every company read on a position: the prefix each one's key extends. */
export const heldCompaniesKey = (projectId: string | null) => ["extension", "held", "company", projectId] as const;

export const heldCompanyKey = (projectId: string | null, linkedinUrl: string | null, name: string | null) =>
  [...heldCompaniesKey(projectId), linkedinUrl, name] as const;

/** How often a fresh record is read again while its research lands, and for how long. */
const RESEARCH_POLL_MS = 5_000;
const RESEARCH_POLL_FOR_MS = 60_000;

/**
 * Whether the position already maps the profile the panel stands on — asked again for every page and every position,
 * so the panel opens the record rather than a form whose save would be refused. While a just-filed person's research
 * has not landed it reads again, so their photo and title arrive without a reload.
 */
export function useHeldPerson(projectId: string | null, linkedinUrl: string | null, awaitingResearchSince: number | null) {
  return useQuery<HeldCandidate | null>({
    queryKey: heldPersonKey(projectId, linkedinUrl),
    enabled: Boolean(projectId && linkedinUrl),
    staleTime: 0,
    queryFn: () => askOrThrow({ kind: "heldCandidate", projectId: projectId!, linkedinUrl: linkedinUrl! }),
    refetchInterval: (query) => {
      const held = query.state.data;
      const stillWaiting =
        awaitingResearchSince !== null && Date.now() - awaitingResearchSince < RESEARCH_POLL_FOR_MS;
      return held && !held.candidate.enrichedAt && stillWaiting ? RESEARCH_POLL_MS : false;
    },
  });
}

/** Whether the position already holds the company on the page, by its LinkedIn page or its name. */
export function useHeldCompany(projectId: string | null, linkedinUrl: string | null, name: string | null) {
  return useQuery<TriagedCompany | null>({
    queryKey: heldCompanyKey(projectId, linkedinUrl, name),
    enabled: Boolean(projectId && (linkedinUrl || name)),
    staleTime: 0,
    queryFn: () => askOrThrow({ kind: "heldCompany", projectId: projectId!, linkedinUrl, name }),
  });
}

export function useHeldCache() {
  const queryClient = useQueryClient();
  return {
    setPerson: (projectId: string, linkedinUrl: string, held: HeldCandidate | null) =>
      queryClient.setQueryData(heldPersonKey(projectId, linkedinUrl), held),
    refreshPerson: (projectId: string, linkedinUrl: string) =>
      queryClient.invalidateQueries({ queryKey: heldPersonKey(projectId, linkedinUrl) }),
    refreshCompany: (projectId: string, linkedinUrl: string | null, name: string | null) =>
      queryClient.invalidateQueries({ queryKey: heldCompanyKey(projectId, linkedinUrl, name) }),
    /** Every company read on the position, dropped rather than shown stale: an undone person may have taken theirs. */
    forgetCompanies: (projectId: string) =>
      queryClient.removeQueries({ queryKey: heldCompaniesKey(projectId) }),
  };
}
