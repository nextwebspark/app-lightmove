import { useMutation } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as triageApi from "../api/triageApi";
import type { TriageCompany } from "../api/types";

/**
 * A note change on its own — the same write the Companies panel and the grid's own inline-edit Note
 * cell both need, so a note edited in either place shows the same way in the other.
 */
export function useSaveCompanyNote(projectId: string, onSaved: () => void) {
  const toast = useToast();
  return useMutation({
    mutationFn: ({ company, note }: { company: TriageCompany; note: string }) =>
      triageApi.updateTriageCompany(projectId, company.id, { note }),
    onSuccess: () => {
      onSaved();
      toast("Note saved");
    },
    onError: (error) => toast(messageFor(error)),
  });
}
