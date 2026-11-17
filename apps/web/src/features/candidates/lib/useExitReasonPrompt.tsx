import { useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason, ExitReasonCatalog, PipelineCatalog, PipelineStatus } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import type { ExitReasonChoice } from "../api/types";
import { ExitReasonDialog } from "../components/ExitReasonDialog";
import { findStatus, liveStatuses } from "./pipelineStatus";

/** The live reasons a status offers, in the list's order. */
export function reasonsOfferedAt(reasons: ExitReasonCatalog | undefined, statusId: string): ExitReason[] {
  return (reasons?.reasons ?? []).filter((reason) => !reason.retired && reason.statusIds.includes(statusId));
}

export function fetchExitReasons(queryClient: ReturnType<typeof useQueryClient>): Promise<ExitReasonCatalog> {
  return queryClient.fetchQuery({
    queryKey: pipelineApi.EXIT_REASONS_KEY,
    queryFn: ({ signal }) => pipelineApi.exitReasons(signal),
    staleTime: 60_000,
  });
}

/**
 * What a move asks about. A picker names the status chosen; a drop on the board's band names the stage's ways out,
 * one of which may still be to choose.
 */
export type ExitMove =
  | { name: string; statusKey: string }
  | { name: string; ways: PipelineStatus[]; stageLabel: string };

/** Carries the move out: the status it lands on (the dialog may have changed it) and the reason given, if any. */
export type ExitProceed = (statusKey: string, reason: ExitReasonChoice | null) => void;

interface Asking {
  name: string;
  stageLabel: string | null;
  ways: PipelineStatus[];
  initialWayId: string | null;
  reasons: ExitReasonCatalog;
  proceed: ExitProceed;
  cancel?: () => void;
}

/**
 * Every move out of process asks the same way, wherever it starts. The dialog opens only for what is still unknown:
 * which way out, when a drop leaves a choice, and why, when that way out offers reasons. Anything else goes straight
 * through. Render `dialog` wherever the hook is used.
 */
export function useExitReasonPrompt(): {
  ask: (move: ExitMove, proceed: ExitProceed, cancel?: () => void) => Promise<void>;
  dialog: ReactNode;
} {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { catalog } = usePipelineCatalog();
  const [asking, setAsking] = useState<Asking | null>(null);

  const ask = async (move: ExitMove, proceed: ExitProceed, cancel?: () => void) => {
    const asked = "statusKey" in move ? fromPicker(catalog, move.statusKey) : fromDrop(move.ways, move.stageLabel);
    if (!asked) {
      if ("statusKey" in move) proceed(move.statusKey, null);
      else cancel?.();
      return;
    }
    let reasons: ExitReasonCatalog;
    try {
      reasons = await fetchExitReasons(queryClient);
    } catch (error) {
      // Sent without a reason, a move to a way out that asks for one is certain to be refused: stop and say so.
      toast.error(messageFor(error));
      cancel?.();
      return;
    }
    const chosen = asked.ways.find((way) => way.id === asked.initialWayId) ?? null;
    if (chosen && reasonsOfferedAt(reasons, chosen.id).length === 0) {
      proceed(chosen.key, null);
      return;
    }
    setAsking({ name: move.name, ...asked, reasons, proceed, cancel });
  };

  const dialog = asking && (
    <ExitReasonDialog
      name={asking.name}
      stageLabel={asking.stageLabel}
      ways={asking.ways}
      initialWayId={asking.initialWayId}
      reasonsOf={(way) => reasonsOfferedAt(asking.reasons, way.id)}
      onCancel={() => {
        setAsking(null);
        asking.cancel?.();
      }}
      onConfirm={(way, choice) => {
        setAsking(null);
        asking.proceed(way.key, choice);
      }}
    />
  );
  return { ask, dialog };
}

type Asked = Pick<Asking, "ways" | "initialWayId" | "stageLabel">;

/** A status picked out of process: its stage's ways out, with the one picked filled in and still changeable. */
function fromPicker(catalog: PipelineCatalog, statusKey: string): Asked | null {
  const found = findStatus(catalog, statusKey);
  if (!found?.status.outOfProcess) return null;
  const ways = liveStatuses(found.stage).filter((status) => status.outOfProcess);
  return {
    ways: ways.some((way) => way.id === found.status.id) ? ways : [found.status, ...ways],
    initialWayId: found.status.id,
    stageLabel: found.stage.label,
  };
}

/** A drop on a stage's cell of the band: its one way out filled in, or none yet where it offers several. */
function fromDrop(ways: PipelineStatus[], stageLabel: string): Asked | null {
  if (ways.length === 0) return null;
  return { ways, initialWayId: ways.length === 1 ? ways[0].id : null, stageLabel };
}
