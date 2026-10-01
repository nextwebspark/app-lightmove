import { useRadioGroupKeys } from "../../../../components/ui/useRadioGroupKeys";
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
  const keys = useRadioGroupKeys(
    TIMELINE_GROUPS.map((option) => option.value),
    value,
    onChange,
  );

  return (
    <div
      ref={keys.ref}
      role="radiogroup"
      aria-label="Kind of activity"
      onKeyDown={keys.onKeyDown}
      className="flex flex-wrap gap-1.5"
    >
      {TIMELINE_GROUPS.map((option) => (
        <button
          key={option.label}
          type="button"
          role="radio"
          aria-checked={value === option.value}
          tabIndex={value === option.value ? 0 : -1}
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
