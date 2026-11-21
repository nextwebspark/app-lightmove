import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
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

interface RecordEntry {
  recordEnteredFor: string | null;
}

export interface OpenRecord {
  profile: OpenProfile | null;
  /** Opens the panel on an executive (into the URL) or on the add form, or closes it with null. */
  setProfile: (next: OpenProfile | null) => void;
  focusTab: DrawerTab | null;
  /** The record full page, `?record=page`. */
  recordPage: boolean;
  setRecordPage: (onPage: boolean) => void;
}

/**
 * A stage's open executive, held in the URL (`?candidate=`, `?tab=` from a link, `?record=page` for the full page) so
 * a refresh, Back or a shared link reopens it; the add form stays local. `offered` is false where another panel reads
 * the same `?candidate=` (the board); `fullPageOffered` where the full page can be drawn (the grid).
 */
export function useOpenRecord(projectId: string, offered: boolean, fullPageOffered: boolean): OpenRecord {
  const queryClient = useQueryClient();
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();
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
      (current) => {
        const params = new URLSearchParams(current);
        if (candidateId) params.set("candidate", candidateId);
        else {
          params.delete("candidate");
          params.delete("record");
        }
        params.delete("note");
        params.delete("section");
        params.delete("tab");
        return params;
      },
      // A save re-opens the same person and keeps how the page was entered; a step does not.
      { replace: true, state: candidateId === openCandidateId ? location.state : undefined },
    );

  const profile: OpenProfile | null =
    adding ??
    (openCandidateId !== null && openCandidate.data?.id === openCandidateId
      ? { candidate: openCandidate.data, company: null }
      : null);
  const recordPage = fullPageOffered && searchParams.get("record") === "page" && profile?.candidate != null;

  // Going in pushes, so Back returns to the peek. Coming out pops that entry while it still holds the person it was
  // pushed for, rather than leaving the same peek twice in history; after a step, or on a loaded link, it replaces.
  const enteredRecordFor = (location.state as RecordEntry | null)?.recordEnteredFor ?? null;
  const setRecordPage = (onPage: boolean) => {
    if (!onPage && enteredRecordFor !== null && enteredRecordFor === openCandidateId) {
      navigate(-1);
      return;
    }
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        if (onPage) params.set("record", "page");
        else params.delete("record");
        return params;
      },
      onPage ? { state: { recordEnteredFor: openCandidateId } satisfies RecordEntry } : { replace: true },
    );
  };

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

  return { profile, setProfile, focusTab, recordPage, setRecordPage };
}
