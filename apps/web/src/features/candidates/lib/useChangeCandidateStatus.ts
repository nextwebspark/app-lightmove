import { useMutation } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, CandidateStatus } from "../api/types";
import { candidateStatusStyle } from "./candidateVocabulary";

/**
 * A status change on its own — the same write the profile's header pill and the Companies grid's
 * Status column both need, so the two never disagree about what changing a candidate's status does.
 */
export function useChangeCandidateStatus(projectId: string, onSaved: (saved: Candidate) => void) {
  const toast = useToast();
  return useMutation({
    mutationFn: ({ candidateId, status }: { candidateId: string; status: CandidateStatus }) =>
      candidatesApi.changeCandidateStatus(projectId, candidateId, status),
    onSuccess: (saved) => {
      onSaved(saved);
      toast(`${saved.fullName} is now ${candidateStatusStyle(saved.status).label.toLowerCase()}`);
    },
    onError: (error) => toast(messageFor(error)),
  });
}
