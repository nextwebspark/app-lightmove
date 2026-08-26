import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

type PillTone = "amber" | "sky" | "red";

const TONES: Record<PillTone, string> = {
  amber: "bg-amber-dim text-amber shadow-[inset_0_0_0_1px_var(--color-amber-dim)]",
  sky: "bg-sky-dim text-sky",
  red: "bg-red-dim text-red",
};

/** One selected value as a pill, removable where the caller can drop it. */
export function SelectionPill({
  label,
  tone = "sky",
  onRemove,
}: {
  label: string;
  tone?: PillTone;
  onRemove?: () => void;
}) {
  return (
    <span
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 rounded-full px-[9px] py-[3px]",
        TONES[tone],
      )}
    >
      <span className="truncate font-sans text-[11px] font-medium">{label}</span>
      {onRemove && (
        <button
          type="button"
          aria-label={`Remove ${label}`}
          onClick={(event) => {
            // The pill row sits inside headers that are themselves the open/close control.
            event.stopPropagation();
            onRemove();
          }}
          className="flex-none opacity-60 transition hover:opacity-100"
        >
          <Icon d={ICONS.close} size={9} />
        </button>
      )}
    </span>
  );
}
