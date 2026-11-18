import type { ReactNode } from "react";
import { cn } from "../../../lib/cn";
import type { CandidateExitReason } from "../api/types";
import { useExitReasonChange } from "../lib/useExitReasonChange";

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
  const reason = useExitReasonChange({ projectId, candidateId, name, statusKey, given, canChange, onChanged });
  if (!reason) return null;

  return (
    <ReasonLabel className={className}>
      {canChange ? (
        <span className="relative inline-flex min-w-0 items-center gap-1 rounded px-1 hover:bg-u-surface" title={reason.hover}>
          <span className={cn("truncate", given ? "text-u-text" : "italic text-u-text3")}>{given?.label ?? "Not given"}</span>
          <span aria-hidden className="text-u-text3">
            ▾
          </span>
          <select
            aria-label={`Why ${name} left`}
            value={given?.reasonId ?? ""}
            disabled={reason.saving}
            onChange={(event) => reason.pick(event.target.value)}
            className="absolute inset-0 cursor-pointer opacity-0"
          >
            {!given && <option value="">Not given</option>}
            {reason.offered.map((option) => (
              <option key={option.id} value={option.id}>
                {option.label}
              </option>
            ))}
          </select>
        </span>
      ) : (
        <span className="truncate text-u-text" title={reason.hover}>
          {given?.label}
        </span>
      )}
      {/* A native select fires nothing for the option already chosen, so a held note gets its own way back in. */}
      {canChange && given?.requiresNote && (
        <button
          type="button"
          disabled={reason.saving}
          onClick={reason.editNote}
          className="flex-none rounded px-1 text-u-accent hover:bg-u-surface"
        >
          Edit note
        </button>
      )}
      {reason.dialog}
    </ReasonLabel>
  );
}

function ReasonLabel({ className, children }: { className?: string; children: ReactNode }) {
  return (
    <span className={cn("flex min-w-0 items-center gap-1.5 font-mono text-meta", className)}>
      <span className="type-summary-label flex-none text-u-text3">Why</span>
      {children}
    </span>
  );
}
