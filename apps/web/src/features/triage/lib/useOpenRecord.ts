import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { useAddressedPanel } from "../../../lib/useAddressedPanel";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import type { CandidateCompanyContext } from "../../candidates/components/CandidateDrawer";
import { POSITION_STAFF_TABS, type DrawerTab } from "../../candidates/lib/drawerTabs";

/** The params that live and die with the open executive: where a link lands inside the record. */
export const OPEN_RECORD_COMPANIONS = ["note", "section", "tab"] as const;

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
  /** Opens the panel on an executive (a step in history from a closed panel) or on the add form, or closes it with null. */
  setProfile: (next: OpenProfile | null) => void;
  /** Moves the record to another executive in place, as Previous and Next do. */
  stepProfile: (candidate: Candidate) => void;
  focusTab: DrawerTab | null;
}

/**
 * A stage's open executive, held in the URL (`?candidate=`, `?tab=` from a link) so a refresh, Back or a shared link
 * reopens it; the add form stays local. `offered` is false where another panel reads the same `?candidate=` (the board).
 */
export function useOpenRecord(projectId: string, offered: boolean): OpenRecord {
  const queryClient = useQueryClient();
  const toast = useToast();
  const [searchParams] = useSearchParams();
  const panel = useAddressedPanel("candidate", OPEN_RECORD_COMPANIONS);
  const [adding, setAdding] = useState<OpenProfile | null>(null);

  const openCandidateId = offered ? panel.value : null;
  const requestedTab = searchParams.get("tab");
  const focusTab = POSITION_STAFF_TABS.find((tab) => tab === requestedTab) ?? null;
  const openCandidate = useQuery({
    queryKey: candidatesApi.CANDIDATE_KEY(projectId, openCandidateId ?? ""),
    queryFn: ({ signal }) => candidatesApi.getCandidate(projectId, openCandidateId!, signal),
    enabled: openCandidateId !== null,
  });

  const profile: OpenProfile | null =
    adding ??
    (openCandidateId !== null && openCandidate.data?.id === openCandidateId
      ? { candidate: openCandidate.data, company: null }
      : null);
  const show = (candidate: Candidate) => {
    queryClient.setQueryData(candidatesApi.CANDIDATE_KEY(projectId, candidate.id), candidate);
    setAdding(null);
  };
  const setProfile = (next: OpenProfile | null) => {
    if (next?.candidate) {
      show(next.candidate);
      panel.open(next.candidate.id);
      return;
    }
    setAdding(next);
    if (openCandidateId !== null) panel.close();
  };
  // A step keeps the entry's state, so the Executives view still knows where it was entered from.
  const stepProfile = (candidate: Candidate) => {
    show(candidate);
    panel.step(candidate.id);
  };

  // Someone removed since the link was made, or out of this reader's reach: say so and leave the grid as it is. A
  // failed refetch of someone still on screen keeps them; a removal drops their cached read, so Back cannot revive them.
  const openCandidateFailed = openCandidateId !== null && openCandidate.isError && openCandidate.data === undefined;
  useEffect(() => {
    if (!openCandidateFailed) return;
    toast.error(messageFor(openCandidate.error));
    panel.close();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [openCandidateFailed]);

  return { profile, setProfile, stepProfile, focusTab };
}
