import { useMutation } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { CaptureCompanyRequest, TriagedCompany } from "../../api/types";
import type { TriageDestination } from "../../domain/triageDestination";

export interface CaptureAttempt {
  projectId: string;
  destination: TriageDestination;
  capture: Omit<CaptureCompanyRequest, "status">;
}

export interface CaptureRefusal {
  code: string;
  message: string;
}

/**
 * Writes the company into the chosen mandate.
 *
 * Refusals arrive as data rather than as an exception message, because the popup has something
 * specific to say about two of them — `TRIAGE_COMPANY_DECLINED` means the team already ruled this
 * company out, and a `FORBIDDEN` means the consultant is not seated on that mandate — and neither is
 * served by a generic "something went wrong".
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
        throw { code: result.code, message: result.message } satisfies CaptureRefusal;
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
