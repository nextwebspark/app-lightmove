import { useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type {
  ExitReason,
  ExitReasonCatalog,
  PipelineCatalog,
  PipelineStatus,
} from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import type { ExitReasonChoice } from "../api/types";
import { ExitReasonDialog } from "../components/ExitReasonDialog";
import { findStatus } from "./pipelineStatus";

interface Asking {
  name: string;
  wayOut: PipelineStatus;
  reasons: ExitReason[];
  proceed: (choice: ExitReasonChoice) => void;
  cancel?: () => void;
}

/** The live reasons a status offers, in the list's order. */
export function reasonsOfferedAt(
  reasons: ExitReasonCatalog | undefined,
  statusId: string,
): ExitReason[] {
  return (reasons?.reasons ?? []).filter(
    (reason) => !reason.retired && reason.statusIds.includes(statusId),
  );
}

export function fetchExitReasons(
  queryClient: ReturnType<typeof useQueryClient>,
): Promise<ExitReasonCatalog> {
  return queryClient.fetchQuery({
    queryKey: pipelineApi.EXIT_REASONS_KEY,
    queryFn: ({ signal }) => pipelineApi.exitReasons(signal),
    staleTime: 60_000,
  });
}

/**
 * Every status change asks why the same way: a move to a way out that offers reasons opens the dialog first, and any
 * other move goes straight through. Render `dialog` wherever the hook is used.
 */
export function useExitReasonPrompt(): {
  ask: (
    name: string,
    statusKey: string,
    proceed: (choice: ExitReasonChoice | null) => void,
    cancel?: () => void,
  ) => Promise<void>;
  dialog: ReactNode;
} {
  const queryClient = useQueryClient();
  const { catalog } = usePipelineCatalog();
  const [asking, setAsking] = useState<Asking | null>(null);

  const ask = async (
    name: string,
    statusKey: string,
    proceed: (choice: ExitReasonChoice | null) => void,
    cancel?: () => void,
  ) => {
    const wayOut = wayOutOf(catalog, statusKey);
    if (!wayOut) return proceed(null);
    let reasons: ExitReason[];
    try {
      reasons = reasonsOfferedAt(
        await fetchExitReasons(queryClient),
        wayOut.id,
      );
    } catch {
      // Unread, the server still decides: a move that needs a reason is refused with a message saying so.
      return proceed(null);
    }
    if (reasons.length === 0) return proceed(null);
    setAsking({ name, wayOut, reasons, proceed, cancel });
  };

  const dialog = asking && (
    <ExitReasonDialog
      name={asking.name}
      wayOut={asking.wayOut.label}
      reasons={asking.reasons}
      onCancel={() => {
        setAsking(null);
        asking.cancel?.();
      }}
      onConfirm={(choice) => {
        setAsking(null);
        asking.proceed(choice);
      }}
    />
  );
  return { ask, dialog };
}

function wayOutOf(
  catalog: PipelineCatalog,
  statusKey: string,
): PipelineStatus | null {
  const status = findStatus(catalog, statusKey)?.status;
  return status?.outOfProcess ? status : null;
}
