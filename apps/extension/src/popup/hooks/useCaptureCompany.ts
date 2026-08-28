import { useMutation } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";
import type { CaptureCompanyRequest, TriagedCompany } from "../../api/types";
import type { TriageDestination } from "../../domain/triageDestination";

export interface CaptureAttempt {
  projectId: string;
  destination: TriageDestination;
  capture: Omit<CaptureCompanyRequest, "status">;
}

/**
 * A refusal the popup can say something specific about.
 *
 * An `Error` rather than a plain object: everything else in the extension narrows failures with
 * `instanceof Error`, and a thrown literal has no stack and no name — it surfaces as
 * `Uncaught (in promise) Object` if it ever escapes React Query.
 */
export class CaptureRefusal extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "CaptureRefusal";
    this.code = code;
  }
}

/**
 * Writes the company into the chosen mandate.
 *
 * Refusals arrive as data rather than as an exception message, because the popup has something
 * specific to say about two of them — `TRIAGE_COMPANY_ALREADY_HELD` means the mandate already carries
 * a company of that name, and `FORBIDDEN` means the consultant is not seated on that mandate — and
 * neither is served by a generic "something went wrong".
 *
 * The server refuses by name and does not distinguish the stage the existing row sits in, so a
 * company the team previously *declined* reports as already held too. Saying more than that would
 * need the API to say more than it does.
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
