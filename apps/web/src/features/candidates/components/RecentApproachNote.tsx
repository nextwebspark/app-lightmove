import { cn } from "../../../lib/cn";
import type { RecentApproach } from "../api/types";
import { approachLines, hasApproach } from "../lib/recentApproach";

/**
 * Recently approached, in one line: the loudest thing to know, amber when going ahead asks first, quiet when it is
 * only history. The detail is in `RecentApproachConfirm`, at the moment someone goes ahead.
 */
export function RecentApproachNote({
  approach,
  className,
}: {
  approach: RecentApproach | null | undefined;
  className?: string;
}) {
  if (!hasApproach(approach)) return null;
  const lines = approachLines(approach);
  if (lines.length === 0) return null;
  const more = lines.length > 1 ? ` · +${lines.length - 1} more` : "";

  if (approach.tier === "INFO") {
    return (
      <p role="note" aria-label="Approached before" className={cn("text-meta text-u-text3", className)}>
        {lines[0].text}
      </p>
    );
  }
  return (
    <p role="note" aria-label="Recently approached" className={cn("flex items-center gap-1.5 text-meta text-u-text2", className)}>
      <span aria-hidden className="size-1.5 flex-none rounded-full bg-u-signal" />
      <b className="font-semibold text-u-signal">Recently approached</b>
      <span className="min-w-0 truncate">
        {lines[0].text}
        {more}
      </span>
    </p>
  );
}
