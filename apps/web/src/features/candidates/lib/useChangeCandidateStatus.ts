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
  const change = useMutation({
    mutationFn: ({ candidateId, status }: ChangeCandidateStatus) =>
      candidatesApi.changeCandidateStatus(projectId, candidateId, status),
    onSuccess: (saved, { candidateId, previous }) => {
      onSaved(saved);
      const message = `${saved.fullName} is now ${candidateStatusStyle(saved.status).label.toLowerCase()}`;
      if (previous === undefined || previous === saved.status) {
        toast(message);
        return;
      }
      toast.success(message, { undo: () => change.mutate({ candidateId, status: previous }) });
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  return change;
}

interface ChangeCandidateStatus {
  candidateId: string;
  status: CandidateStatus;
  /** The status being left; given, the toast offers an Undo back to it. */
  previous?: CandidateStatus;
}
