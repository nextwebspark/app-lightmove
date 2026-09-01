import { useMutation } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { CapturedCandidate, SaveCandidateRequest } from "../../api/types";
import { CaptureRefusal } from "../lib/captureRefusal";

export interface CandidateAttempt {
  projectId: string;
  candidate: SaveCandidateRequest;
}

/**
 * Writes the person into the chosen mandate's people.
 *
 * Refusals arrive as data rather than as an exception message: `CANDIDATE_ALREADY_MAPPED` and
 * `FORBIDDEN` are both things the consultant can act on, and neither is served by a generic
 * "something went wrong".
 */
export function useCapturePerson() {
  const capture = useMutation<CapturedCandidate, CaptureRefusal, CandidateAttempt>({
    mutationFn: async ({ projectId, candidate }) => {
      const result = await askServiceWorker({ kind: "captureCandidate", projectId, candidate });
      if (!result.ok) {
        throw new CaptureRefusal(result.code, result.message);
      }
      return result.value;
    },
  });

  return {
    save: capture.mutate,
    saved: capture.data ?? null,
    isSaving: capture.isPending,
    refusal: capture.error ?? null,
    reset: capture.reset,
  };
}
