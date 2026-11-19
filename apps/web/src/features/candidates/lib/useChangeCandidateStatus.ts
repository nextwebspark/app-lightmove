import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as candidatesApi from "../api/candidatesApi";
import type { Candidate, CandidateStatus, ExitReasonChoice } from "../api/types";
import { statusStyle } from "./pipelineStatus";
import { useExitReasonPrompt } from "./useExitReasonPrompt";

/**
 * A status change on its own — the same write the profile's header pill and the Companies grid's
 * Status column both need, so the two never disagree about what changing a candidate's status does.
 * A move to a way out that offers reasons asks why first: render `dialog` beside the picker.
 */
export function useChangeCandidateStatus(projectId: string, onSaved: (saved: Candidate) => void) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const prompt = useExitReasonPrompt();
  const change = useMutation({
    mutationFn: ({ candidateId, status, reason }: ChangeCandidateStatus & { reason: ExitReasonChoice | null }) =>
      candidatesApi.changeCandidateStatus(projectId, candidateId, status, reason),
    onSuccess: (saved, { candidateId, previous }) => {
      onSaved(saved);
      void queryClient.invalidateQueries({ queryKey: candidatesApi.EXIT_REASONS_OF_KEY(projectId) });
      const message = `${saved.fullName} is now ${statusStyle(saved.status, saved).label.toLowerCase()}`;
      if (previous === undefined || previous === saved.status) {
        toast(message);
        return;
      }
      // Back to a way out that offers reasons asks why again: the reason they had went with the move.
      toast.success(message, { undo: () => request({ candidateId, name: saved.fullName, status: previous }) });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const request = (args: ChangeCandidateStatus, options?: { onSettled?: () => void }) =>
    void prompt.ask(
      { name: args.name, statusKey: args.status },
      // The dialog may have changed the way out: the status sent is the one it confirmed. Not mutate's own onSettled:
      // React Query calls that for the latest call alone, so a second row changed mid-save left the first row busy.
      (status, reason) =>
        void change
          .mutateAsync({ ...args, status, reason })
          // Not swallowed: the mutation's own onError has already said why in a toast.
          .catch(() => undefined)
          .finally(() => options?.onSettled?.()),
      options?.onSettled,
    );

  // isPending is the latest change's alone: a surface changing several rows tracks each one itself, as the grid's
  // busy ids do.
  return { mutate: request, isPending: change.isPending, dialog: prompt.dialog };
}

interface ChangeCandidateStatus {
  candidateId: string;
  /** Who is moving, for the dialog that asks why they left. */
  name: string;
  status: CandidateStatus;
  /** The status being left; given, the toast offers an Undo back to it. */
  previous?: CandidateStatus;
}
