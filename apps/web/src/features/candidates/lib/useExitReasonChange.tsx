import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason, PipelineStatus } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import { CANDIDATES_KEY_PREFIX, changeExitReason, EXIT_REASONS_OF_KEY } from "../api/candidatesApi";
import type { CandidateExitReason, ExitReasonChoice } from "../api/types";
import { ExitReasonDialog } from "../components/ExitReasonDialog";
import { findStatus } from "./pipelineStatus";
import { reasonsOfferedAt } from "./useExitReasonPrompt";

export interface ExitReasonChange {
  wayOut: PipelineStatus;
  offered: ExitReason[];
  hover: string;
  saving: boolean;
  pick: (reasonId: string) => void;
  editNote: () => void;
  /** The note dialog while one is asked for; render it beside the control. */
  dialog: ReactNode;
}

/**
 * Changing the reason of someone out of process where it is read, for the Why line and the drawer's Reason pill alike:
 * picking a reason saves it, and one that asks for a note opens the dialog for it. Null while they are in process, or
 * when there is nothing to show (no reason given, and none can be).
 */
export function useExitReasonChange({
  projectId,
  candidateId,
  name,
  statusKey,
  given,
  canChange,
  onChanged,
}: {
  projectId: string;
  candidateId: string;
  name: string;
  statusKey: string;
  given: CandidateExitReason | null;
  canChange: boolean;
  onChanged?: (saved: CandidateExitReason) => void;
}): ExitReasonChange | null {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { catalog } = usePipelineCatalog();
  const wayOut = findStatus(catalog, statusKey)?.status;
  const isOut = wayOut?.outOfProcess === true;
  const [noting, setNoting] = useState<ExitReasonChoice | null>(null);

  const reasons = useQuery({
    queryKey: pipelineApi.EXIT_REASONS_KEY,
    queryFn: ({ signal }) => pipelineApi.exitReasons(signal),
    enabled: isOut && canChange,
    staleTime: 60_000,
  });
  const change = useMutation({
    mutationFn: (choice: ExitReasonChoice) => changeExitReason(projectId, candidateId, choice),
    onSuccess: (saved) => {
      setNoting(null);
      void queryClient.invalidateQueries({ queryKey: EXIT_REASONS_OF_KEY(projectId) });
      // The position's rows and this person's timeline both hang off it: the card's reason and the new line.
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      toast(`Reason saved: ${saved.label}`);
      onChanged?.(saved);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (!isOut || !wayOut) return null;

  const offered = optionsOf(reasonsOfferedAt(reasons.data, wayOut.id), given);
  // A way out that offers no reasons asks none, so there is nothing missing to say.
  if (!given && (!canChange || offered.length === 0)) return null;

  const keptNote = (reasonId: string) => (reasonId === given?.reasonId ? given.note : null);
  const pick = (reasonId: string) => {
    const reason = offered.find((option) => option.id === reasonId);
    if (!reason) return;
    if (reason.requiresNote) setNoting({ reasonId, note: keptNote(reasonId) });
    else change.mutate({ reasonId, note: keptNote(reasonId) });
  };

  return {
    wayOut,
    offered,
    hover: given ? hoverOf(given) : "Nobody has said why yet",
    saving: change.isPending,
    pick,
    editNote: () => given && setNoting({ reasonId: given.reasonId, note: given.note }),
    dialog: noting && (
      <ExitReasonDialog
        name={name}
        stageLabel={null}
        ways={[wayOut]}
        initialWayId={wayOut.id}
        reasonsOf={() => offered}
        purpose={{ kind: "change", onSave: (choice) => change.mutate(choice) }}
        initial={noting}
        saving={change.isPending}
        onCancel={() => setNoting(null)}
      />
    ),
  };
}

/** What the status offers now, and the reason held even once it is retired or unlinked, so the picker can show it. */
function optionsOf(offered: ExitReason[], given: CandidateExitReason | null): ExitReason[] {
  if (!given || offered.some((reason) => reason.id === given.reasonId)) return offered;
  return [
    {
      id: given.reasonId,
      key: given.reasonId,
      label: given.label,
      requiresNote: given.requiresNote,
      position: -1,
      retired: given.retired,
      statusIds: [],
    },
    ...offered,
  ];
}

function hoverOf(given: CandidateExitReason): string {
  const by = [given.setByName, given.setAt ? formatInstantDate(given.setAt) : null].filter(Boolean).join(" on ");
  return [given.note, by ? `Set by ${by}` : null].filter(Boolean).join("\n");
}
