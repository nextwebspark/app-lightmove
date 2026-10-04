import { cn } from "../../../../lib/cn";
import type { CandidateTag } from "../../api/types";
import { tagClassName } from "../../lib/tagStyle";

/** One tag as a pill, in its own swatch. */
export function TagPill({ tag, className }: { tag: CandidateTag; className?: string }) {
  return (
    <span
      className={cn(
        "inline-block min-w-0 max-w-[140px] shrink truncate align-middle rounded-full px-2 py-px font-mono text-[10.5px] font-medium",
        tagClassName(tag.colour),
        tag.retired && "opacity-60",
        className,
      )}
      title={tag.retired ? `${tag.label} (retired)` : tag.label}
    >
      {tag.label}
    </span>
  );
}
