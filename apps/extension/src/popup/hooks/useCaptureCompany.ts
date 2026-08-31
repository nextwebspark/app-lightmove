import { useMutation } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { CaptureCompanyRequest, TriagedCompany } from "../../api/types";
import type { TriageDestination } from "../../domain/triageDestination";
import { CaptureRefusal } from "../lib/captureRefusal";

export interface CaptureAttempt {
  projectId: string;
  destination: TriageDestination;
  capture: Omit<CaptureCompanyRequest, "status">;
}

/**
 * Writes the company into the chosen mandate.
 *
 * Refusals arrive as data, because the popup says something specific about two of them. The server
 * refuses by name whatever stage the existing row sits at, so a previously declined company reports
 * as already held too.
 */
export function useCaptureCompany() {
  const capture = useMutation<TriagedCompany, CaptureRefusal, CaptureAttempt>({
    mutationFn: async ({ projectId, destination, capture: fields }) => {
      const result = await askServiceWorker({
        kind: "captureCompany",
        projectId,
        capture: { ...fields, status: destination },
      });
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
