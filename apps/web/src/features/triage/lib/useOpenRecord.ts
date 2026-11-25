import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocation, useSearchParams } from "react-router-dom";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import type { CandidateCompanyContext } from "../../candidates/components/CandidateDrawer";
import { POSITION_STAFF_TABS, type DrawerTab } from "../../candidates/lib/drawerTabs";

/**
 * What the profile drawer is open on: an existing executive, or a blank one at a known company. Both
 * null is the toolbar's "Add executive", which maps someone with no company at all.
 */
export interface OpenProfile {
  candidate: Candidate | null;
  company: CandidateCompanyContext | null;
}

export interface OpenRecord {
  profile: OpenProfile | null;
  /** Opens the panel on an executive (into the URL) or on the add form, or closes it with null. */
  setProfile: (next: OpenProfile | null) => void;
  focusTab: DrawerTab | null;
}

/**
 * A stage's open executive, held in the URL (`?candidate=`, `?tab=` from a link) so a refresh, Back or a shared link
 * reopens it; the add form stays local. `offered` is false where another panel reads the same `?candidate=` (the board).
 */
export function useOpenRecord(projectId: string, offered: boolean): OpenRecord {
  const queryClient = useQueryClient();
  const toast = useToast();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [adding, setAdding] = useState<OpenProfile | null>(null);

  const openCandidateId = offered ? searchParams.get("candidate") : null;
  const requestedTab = searchParams.get("tab");
  const focusTab = POSITION_STAFF_TABS.find((tab) => tab === requestedTab) ?? null;
  const openCandidate = useQuery({
    queryKey: candidatesApi.CANDIDATE_KEY(projectId, openCandidateId ?? ""),
    queryFn: ({ signal }) => candidatesApi.getCandidate(projectId, openCandidateId!, signal),
    enabled: openCandidateId !== null,
  });
  const setOpenCandidateParam = (candidateId: string | null) =>
    setSearchParams(
      (current) => withOpenCandidate(current, candidateId),
      // Kept across a step, so the Executives view still knows where it was entered from.
      { replace: true, state: location.state },
    );

  const profile: OpenProfile | null =
    adding ??
    (openCandidateId !== null && openCandidate.data?.id === openCandidateId
      ? { candidate: openCandidate.data, company: null }
      : null);
  const setProfile = (next: OpenProfile | null) => {
    if (next?.candidate) {
      queryClient.setQueryData(candidatesApi.CANDIDATE_KEY(projectId, next.candidate.id), next.candidate);
      setAdding(null);
      setOpenCandidateParam(next.candidate.id);
      return;
    }
    setAdding(next);
    if (openCandidateId !== null) setOpenCandidateParam(null);
  };

  // Someone removed since the link was made, or out of this reader's reach: say so and leave the grid as it is. A
  // failed refetch of someone still on screen keeps them; a removal drops their cached read, so Back cannot revive them.
  const openCandidateFailed = openCandidateId !== null && openCandidate.isError && openCandidate.data === undefined;
  useEffect(() => {
    if (!openCandidateFailed) return;
    toast.error(messageFor(openCandidate.error));
    setOpenCandidateParam(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openCandidateFailed]);

  return { profile, setProfile, focusTab };
}

/** The URL with `candidate` open (or none), dropping what pointed inside the previous record. */
export function withOpenCandidate(current: URLSearchParams, candidateId: string | null): URLSearchParams {
  const params = new URLSearchParams(current);
  if (candidateId) params.set("candidate", candidateId);
  else params.delete("candidate");
  params.delete("note");
  params.delete("section");
  params.delete("tab");
  return params;
}
