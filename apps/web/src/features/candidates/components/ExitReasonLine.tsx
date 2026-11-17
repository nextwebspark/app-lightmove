import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import * as pipelineApi from "../../workspace/api/pipelineApi";
import type { ExitReason } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import {
  CANDIDATES_KEY_PREFIX,
  changeExitReason,
  EXIT_REASONS_OF_KEY,
} from "../api/candidatesApi";
import type { CandidateExitReason, ExitReasonChoice } from "../api/types";
import { findStatus } from "../lib/pipelineStatus";
import { reasonsOfferedAt } from "../lib/useExitReasonPrompt";
import { ExitReasonDialog } from "./ExitReasonDialog";

/**
 * "Why · Compensation ▾" for someone out of process, changed where it is read: picking a reason saves it, and one that
 * asks for a note (Other) opens the dialog for it. Someone moved out before reasons were asked reads "Not given".
 * Nothing at all while they are in process. Staff-only, like the reason.
 */
export function ExitReasonLine({
  projectId,
  candidateId,
  name,
  statusKey,
  given,
  canChange,
  onChanged,
  className,
}: {
  projectId: string;
  candidateId: string;
  name: string;
  statusKey: string;
  given: CandidateExitReason | null;
  canChange: boolean;
  onChanged?: (saved: CandidateExitReason) => void;
  className?: string;
}) {
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
    mutationFn: (choice: ExitReasonChoice) =>
      changeExitReason(projectId, candidateId, choice),
    onSuccess: (saved) => {
      setNoting(null);
      void queryClient.invalidateQueries({
        queryKey: EXIT_REASONS_OF_KEY(projectId),
      });
      // The position's rows and this person's timeline both hang off it: the card's reason and the new line.
      void queryClient.invalidateQueries({
        queryKey: CANDIDATES_KEY_PREFIX(projectId),
      });
      toast(`Reason saved: ${saved.label}`);
      onChanged?.(saved);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (!isOut || !wayOut) return null;

  const offered = optionsOf(reasonsOfferedAt(reasons.data, wayOut.id), given);
  // A way out that offers no reasons asks none, so there is nothing missing to say.
  if (!given && (!canChange || offered.length === 0)) return null;

  const hover = given ? hoverOf(given) : "Nobody has said why yet";
  const handlePick = (reasonId: string) => {
    const reason = offered.find((option) => option.id === reasonId);
    if (!reason) return;
    if (reason.requiresNote) {
      setNoting({
        reasonId,
        note: reasonId === given?.reasonId ? given.note : null,
      });
      return;
    }
    change.mutate({
      reasonId,
      note: reasonId === given?.reasonId ? given.note : null,
    });
  };

  return (
    <ReasonLabel className={className}>
      {canChange ? (
        <span
          className="relative inline-flex min-w-0 items-center gap-1 rounded px-1 hover:bg-u-surface"
          title={hover}
        >
          <span
            className={cn(
              "truncate",
              given ? "text-u-text" : "italic text-u-text3",
            )}
          >
            {given?.label ?? "Not given"}
          </span>
          <span aria-hidden className="text-u-text3">
            ▾
          </span>
          <select
            aria-label={`Why ${name} left`}
            value={given?.reasonId ?? ""}
            disabled={change.isPending}
            onChange={(event) => handlePick(event.target.value)}
            className="absolute inset-0 cursor-pointer opacity-0"
          >
            {!given && <option value="">Not given</option>}
            {offered.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </span>
      ) : (
        <span className="truncate text-u-text" title={hover}>
          {given?.label}
        </span>
      )}
      {noting && (
        <ExitReasonDialog
          name={name}
          wayOut={wayOut.label}
          reasons={offered}
          editing
          initial={noting}
          saving={change.isPending}
          onCancel={() => setNoting(null)}
          onConfirm={(choice) => change.mutate(choice)}
        />
      )}
    </ReasonLabel>
  );
}

function ReasonLabel({
  className,
  children,
}: {
  className?: string;
  children: ReactNode;
}) {
  return (
    <span
      className={cn(
        "flex min-w-0 items-center gap-1.5 font-mono text-meta",
        className,
      )}
    >
      <span className="type-summary-label flex-none text-u-text3">Why</span>
      {children}
    </span>
  );
}

/** What the status offers now, and the reason held even once it is retired or unlinked, so the select can show it. */
function optionsOf(
  offered: ExitReason[],
  given: CandidateExitReason | null,
): ExitReason[] {
  if (!given || offered.some((reason) => reason.id === given.reasonId))
    return offered;
  return [
    {
      id: given.reasonId,
      key: given.reasonId,
      label: given.label,
      requiresNote: false,
      position: -1,
      retired: given.retired,
      statusIds: [],
    },
    ...offered,
  ];
}

function hoverOf(given: CandidateExitReason): string {
  const by = [
    given.setByName,
    given.setAt ? formatInstantDate(given.setAt) : null,
  ]
    .filter(Boolean)
    .join(" on ");
  return [given.note, by ? `Set by ${by}` : null].filter(Boolean).join("\n");
}
