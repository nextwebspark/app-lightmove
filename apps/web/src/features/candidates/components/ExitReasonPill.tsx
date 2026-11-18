import { Icon, ICONS } from "../../../components/layout/Icon";
import { PickList } from "../../../components/ui/PickList";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { CandidateExitReason } from "../api/types";
import { useExitReasonChange } from "../lib/useExitReasonChange";

/** The status picker's trigger, so the two read as one pair. */
const REASON_PILL =
  "inline-flex h-7 min-w-0 max-w-full items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-2 text-meta font-medium text-u-text transition hover:border-u-text3 disabled:cursor-not-allowed disabled:opacity-40";

/**
 * "Reason  Compensation ▾" beside the status picker in the drawer header, for whoever may change it: the same shape
 * and the same list. Picking the held reason again reopens a note it asks for.
 */
export function ExitReasonPill({
  projectId,
  candidateId,
  name,
  statusKey,
  given,
  className,
}: {
  projectId: string;
  candidateId: string;
  name: string;
  statusKey: string;
  given: CandidateExitReason | null;
  className?: string;
}) {
  const reason = useExitReasonChange({ projectId, candidateId, name, statusKey, given, canChange: true });
  if (!reason) return null;

  return (
    <span className={cn("flex min-w-0 items-center gap-1.5", className)} title={reason.hover}>
      <Popover
        label={`Why ${name} left: ${given?.label ?? "Not given"}`}
        hasPopup="listbox"
        placement="auto"
        width={280}
        disabled={reason.saving}
        triggerClassName={REASON_PILL}
        trigger={(open) => (
          <>
            <span className="flex-none font-normal text-u-text3">Reason</span>
            <span className={cn("truncate", given ? "text-u-text" : "italic text-u-text3")}>
              {given?.label ?? "Not given"}
            </span>
            <Icon
              d={ICONS.chevronDown}
              size={12}
              className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
            />
          </>
        )}
      >
        {(close) => (
          <PickList
            label={`Why ${name} left`}
            options={reason.offered}
            value={given?.reasonId ?? null}
            emptyText="No reasons offered"
            onPick={(reasonId) => {
              close();
              const held = reasonId === given?.reasonId;
              if (!held || given?.requiresNote) reason.pick(reasonId);
            }}
          />
        )}
      </Popover>
      {given?.requiresNote && (
        <button
          type="button"
          disabled={reason.saving}
          onClick={reason.editNote}
          className="flex-none rounded px-1 font-mono text-meta text-u-accent hover:bg-u-surface"
        >
          Edit note
        </button>
      )}
      {reason.dialog}
    </span>
  );
}
