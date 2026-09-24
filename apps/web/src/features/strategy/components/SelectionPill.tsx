import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

type PillTone = "accent" | "offlimits";

const TONES: Record<PillTone, string> = {
  accent: "bg-u-accent-tint text-u-accent",
  offlimits: "bg-u-offlimits-tint text-u-offlimits",
};

/** One selected value as a pill, removable where the caller can drop it. */
export function SelectionPill({
  label,
  tone = "accent",
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
