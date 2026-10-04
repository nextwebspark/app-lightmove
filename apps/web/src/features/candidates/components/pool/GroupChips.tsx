import { Chip } from "../../../../components/ui/Chip";
import { useRadioGroupKeys } from "../../../../components/ui/useRadioGroupKeys";
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
        <Chip
          key={option.label}
          role="radio"
          aria-checked={value === option.value}
          tabIndex={value === option.value ? 0 : -1}
          selected={value === option.value}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </Chip>
      ))}
    </div>
  );
}
