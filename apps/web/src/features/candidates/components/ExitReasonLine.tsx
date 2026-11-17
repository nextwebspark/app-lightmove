import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useToast } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
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
  variant = "line",
  className,
}: {
  projectId: string;
  candidateId: string;
  name: string;
  statusKey: string;
  given: CandidateExitReason | null;
  canChange: boolean;
  onChanged?: (saved: CandidateExitReason) => void;
  /** `pill` sits beside the status picker in the drawer header, in the picker's own shape. */
  variant?: "line" | "pill";
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

  const valueText = (
    <span
      className={cn(
        "truncate",
        given ? "text-u-text" : "italic text-u-text3",
      )}
    >
      {given?.label ?? "Not given"}
    </span>
  );
  const picker = canChange && (
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
  );
  // A native select fires nothing for the option already chosen, so a held note gets its own way back in.
  const editNote = canChange && given?.requiresNote && (
    <button
      type="button"
      disabled={change.isPending}
      onClick={() => setNoting({ reasonId: given.reasonId, note: given.note })}
      className="flex-none rounded px-1 font-mono text-meta text-u-accent hover:bg-u-surface"
    >
      Edit note
    </button>
  );
  const dialog = noting && (
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
  );

  if (variant === "pill") {
    const pillContent = (open: boolean) => (
      <>
        <span className="flex-none font-normal text-u-text3">Reason</span>
        {valueText}
        {canChange && (
          <Icon
            d={ICONS.chevronDown}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
          />
        )}
      </>
    );
    return (
      <span className={cn("flex min-w-0 items-center gap-1.5", className)} title={hover}>
        {canChange ? (
          <Popover
            label={`Why ${name} left: ${given?.label ?? "Not given"}`}
            hasPopup="listbox"
            placement="auto"
            width={280}
            disabled={change.isPending}
            triggerClassName={REASON_PILL}
            trigger={pillContent}
          >
            {(close) => (
              <ReasonList
                label={`Why ${name} left`}
                options={offered}
                heldId={given?.reasonId ?? null}
                onPick={(reasonId) => {
                  close();
                  if (reasonId !== given?.reasonId || offered.find((option) => option.id === reasonId)?.requiresNote) {
                    handlePick(reasonId);
                  }
                }}
              />
            )}
          </Popover>
        ) : (
          <span className={REASON_PILL}>{pillContent(false)}</span>
        )}
        {editNote}
        {dialog}
      </span>
    );
  }

  return (
    <ReasonLabel className={className}>
      {canChange ? (
        <span
          className="relative inline-flex min-w-0 items-center gap-1 rounded px-1 hover:bg-u-surface"
          title={hover}
        >
          {valueText}
          <span aria-hidden className="text-u-text3">
            ▾
          </span>
          {picker}
        </span>
      ) : (
        <span className="truncate text-u-text" title={hover}>
          {given?.label}
        </span>
      )}
      {editNote}
      {dialog}
    </ReasonLabel>
  );
}

/** The status picker's trigger, so the two read as one pair. */
const REASON_PILL =
  "inline-flex h-7 min-w-0 max-w-full items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-2 text-meta font-medium text-u-text transition hover:border-u-text3 disabled:cursor-not-allowed disabled:opacity-40";

/** The status picker's rows: the held one ticked, the rest a hover away. */
function ReasonList({
  label,
  options,
  heldId,
  onPick,
}: {
  label: string;
  options: ExitReason[];
  heldId: string | null;
  onPick: (reasonId: string) => void;
}) {
  return (
    <div role="listbox" aria-label={label} className="flex flex-col">
      {options.map((option) => {
        const isHeld = option.id === heldId;
        return (
          <div
            key={option.id}
            role="option"
            aria-selected={isHeld}
            tabIndex={0}
            onClick={() => onPick(option.id)}
            onKeyDown={(event) => {
              if (event.key === "Enter" || event.key === " ") {
                event.preventDefault();
                onPick(option.id);
              }
            }}
            className={cn(
              "flex cursor-pointer items-center gap-2 rounded-u-control px-2 py-1.5 text-note text-u-text2 outline-none hover:bg-u-raised hover:text-u-text focus-visible:bg-u-raised",
              isHeld && "font-medium text-u-text",
            )}
          >
            <span className="min-w-0 flex-1 truncate">{option.label}</span>
            {isHeld && <Icon d={ICONS.check} size={13} className="flex-none text-u-accent" />}
          </div>
        );
      })}
      {options.length === 0 && <p className="px-2 py-3 text-note text-u-text3">No reasons offered</p>}
    </div>
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
      requiresNote: given.requiresNote,
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
