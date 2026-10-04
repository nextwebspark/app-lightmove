import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as runApi from "../api/runApi";
import type { OutreachRun } from "../api/runApi";

/** Stop, with the toast the mockup words; the page and the drawer share it. */
export function useStopRun(projectId: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (run: Pick<OutreachRun, "id" | "fullName">) => runApi.stopRun(projectId, run.id),
    onSuccess: (_, run) => toast(run.fullName ? `Stopped. Nothing more goes to ${run.fullName}.` : "Sequence stopped."),
    onError: (error) => toast(messageFor(error)),
    // The page's table, the drawer's fold and the sequence cards all sit under this one prefix.
    onSettled: () => void queryClient.invalidateQueries({ queryKey: ["outreach", projectId] }),
  });
}
