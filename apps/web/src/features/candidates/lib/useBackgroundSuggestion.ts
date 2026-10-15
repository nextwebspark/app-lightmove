import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";
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
      toast("AI suggestions added — saving the Background section confirms them");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const nationalityReading = reading.data ?? null;
  const isOffered =
    canWrite &&
    reading.isSuccess &&
    (candidate.gender === null ||
      candidate.yearsExperience === null ||
      candidate.seniority === null ||
      (candidate.nationality === null && nationalityReading === null));

  return {
    nationalityReading,
    isOffered,
    isSuggesting: suggestion.isPending,
    suggest: () => suggestion.mutate(),
  };
}
