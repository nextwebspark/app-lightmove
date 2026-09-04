import { useMutation } from "@tanstack/react-query";
import { askServiceWorker } from "../../background/extensionMessages";

/**
 * Removes the row a capture just wrote.
 *
 * A real delete rather than a soft one, because that is what the mandate's own screens do: removing a
 * company drops the project↔company row and unmaps its executives, and removing a person deletes the
 * candidate. Both endpoints already exist — undo adds nothing to the server.
 */
export function useUndoCapture() {
  const undo = useMutation({
    mutationFn: async (target: { projectId: string; triageCompanyId: string } | { projectId: string; candidateId: string }) => {
      const result = await askServiceWorker(
        "candidateId" in target
          ? { kind: "removeCandidate", projectId: target.projectId, candidateId: target.candidateId }
          : { kind: "removeTriageCompany", projectId: target.projectId, triageCompanyId: target.triageCompanyId },
      );
      if (!result.ok) {
        throw new Error(result.message);
      }
      return null;
    },
  });

  return { undo: undo.mutate, isUndoing: undo.isPending, hasUndone: undo.isSuccess, reset: undo.reset };
}
