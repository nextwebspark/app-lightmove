import { cn } from "../../../../lib/cn";
import type { TimelineGroup } from "../../api/types";
import { TIMELINE_GROUPS } from "../../lib/timelineGroups";

/** The timeline's group chips, shared by the feed and a person's own timeline. */
export function GroupChips({
  value,
  onChange,
}: {
  value: TimelineGroup | "";
  onChange: (group: TimelineGroup | "") => void;
}) {
  return (
    <div role="radiogroup" aria-label="Kind of activity" className="flex flex-wrap gap-1.5">
      {TIMELINE_GROUPS.map((option) => (
        <button
          key={option.label}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          onClick={() => onChange(option.value)}
          className={cn(
            "rounded-full border px-2.5 py-0.5 font-mono text-[11px] transition",
            value === option.value
              ? "border-u-accent bg-u-accent-tint text-u-accent"
              : "border-u-border text-u-text3 hover:text-u-text2",
          )}
        >
          {option.label}
        </button>
      ))}
    </div>
  );
}
