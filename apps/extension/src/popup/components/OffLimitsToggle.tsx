import { cn } from "../lib/cn";
import { Icon, ICONS } from "./Icon";

interface OffLimitsToggleProps {
  isOffLimits: boolean;
  onToggle: (isOffLimits: boolean) => void;
}

/**
 * Records the person as off-limits — the mandate's note that they are not to be approached.
 *
 * It is a candidate status, not an enforcement: nothing downstream blocks an approach, so the label
 * says what the row will hold rather than promising a guard the server does not have.
 */
export function OffLimitsToggle({ isOffLimits, onToggle }: OffLimitsToggleProps) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={isOffLimits}
      onClick={() => onToggle(!isOffLimits)}
      className={cn(
        "flex w-full items-center gap-2.5 rounded-lg border px-2.5 py-2 text-left",
        isOffLimits ? "border-red bg-red-dim" : "border-line bg-panel2",
      )}
    >
      <span
        aria-hidden
        className={cn(
          "grid h-[15px] w-[15px] shrink-0 place-items-center rounded-[4px] border text-[10px]",
          isOffLimits ? "border-red bg-red text-white" : "border-line",
        )}
      >
        {isOffLimits && <Icon d={ICONS.check} size={10} />}
      </span>
      <span className="flex flex-col">
        <span className={cn("text-[12.5px] font-medium", isOffLimits ? "text-red" : "text-text")}>
          Mark off-limits
        </span>
        <span className="text-[10.5px] text-text3">Records that this person is not to be approached</span>
      </span>
    </button>
  );
}
