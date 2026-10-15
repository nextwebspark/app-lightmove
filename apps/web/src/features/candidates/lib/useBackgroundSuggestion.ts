import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { isBillingRefusal } from "../../billing/lib/billingView";
import * as candidatesApi from "../api/candidatesApi";
import { PERSON_TIMELINE_KEY } from "../api/personCrmApi";
import type { Candidate } from "../api/types";

/**
 * The Background section's AI: the nationality classifier's last reading, and Suggest with AI — offered only while
 * something is still missing that the AI has not already read. Staff-only: neither is asked for without `canWrite`.
 */
export function useBackgroundSuggestion(
  projectId: string,
  candidate: Candidate,
  canWrite: boolean,
  onSaved: (candidate: Candidate) => void,
) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const readingKey = candidatesApi.NATIONALITY_READING_KEY(projectId, candidate.id);
  const reading = useQuery({
    queryKey: readingKey,
    queryFn: ({ signal }) => candidatesApi.getNationalityReading(projectId, candidate.id, signal),
    enabled: canWrite,
  });

  const suggestion = useMutation({
    mutationFn: () => candidatesApi.suggestBackground(projectId, candidate.id),
    onSuccess: (saved) => {
      onSaved(saved);
      void queryClient.invalidateQueries({ queryKey: readingKey });
      void queryClient.invalidateQueries({ queryKey: PERSON_TIMELINE_KEY(projectId, candidate.id) });
      toast("The AI has read the background — what it suggests is marked AI");
    },
    onError: (error) => {
      if (!isBillingRefusal(error)) toast.error(messageFor(error));
    },
  });

  const nationalityReading = reading.data ?? null;
  const isOffered =
    canWrite &&
    (candidate.gender === null ||
      candidate.yearsExperience === null ||
      candidate.seniority === null ||
      (candidate.nationality === null && reading.isSuccess && nationalityReading === null));

  return {
    nationalityReading,
    isOffered,
    isSuggesting: suggestion.isPending,
    suggest: () => suggestion.mutate(),
  };
}
