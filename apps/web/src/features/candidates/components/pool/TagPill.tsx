import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import type { CandidateTag } from "../../api/types";
import { tagClassName } from "../../lib/tagStyle";

/** One tag as a pill, in its own swatch — with its own × when it can be taken off. */
export function TagPill({
  tag,
  className,
  onRemove,
  removing,
}: {
  tag: CandidateTag;
  className?: string;
  onRemove?: () => void;
  removing?: boolean;
}) {
  const title = tag.retired ? `${tag.label} (retired)` : tag.label;
  const swatch = cn(
    "min-w-0 max-w-[140px] shrink rounded-full py-px font-mono text-[10.5px] font-medium",
    tagClassName(tag.colour),
    tag.retired && "opacity-60",
  );

  if (!onRemove) {
    return (
      <span className={cn("inline-block truncate px-2 align-middle", swatch, className)} title={title}>
        {tag.label}
      </span>
    );
  }

  return (
    <span className={cn("inline-flex items-center gap-0.5 ps-2 pe-0.5", swatch, className)} title={title}>
      <span className="min-w-0 truncate">{tag.label}</span>
      <button
        type="button"
        aria-label={`Remove tag ${tag.label}`}
        onClick={onRemove}
        disabled={removing}
        className="grid size-3.5 flex-none place-items-center rounded-full opacity-60 transition-opacity hover:opacity-100 focus-visible:opacity-100 disabled:opacity-30"
      >
        <Icon d={ICONS.close} size={9} />
      </button>
    </span>
  );
}
