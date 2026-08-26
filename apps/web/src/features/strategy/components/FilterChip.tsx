import { cn } from "../../../lib/cn";

/**
 * One selectable value as a pill — the wireframe's Location panel, and only that panel.
 *
 * <p>Pills are right for six GCC countries, where every value fits on two rows and the shape of the
 * set is the information. They are wrong for eleven headcount bands or twenty sector groups, which
 * is why every other panel is a checkbox list: an ordered axis read as wrapped pills loses the order,
 * and a long list of pills is a wall rather than a filter.
 *
 * <p>The count follows the current selection — every other axis applied, this one left out — so it
 * answers "how many are left if I click this". It is absent only when the counts read was refused,
 * where a universe total standing in would answer a different question without saying so.
 */
export function FilterChip({
  label,
  count,
  selected,
  onToggle,
}: {
  label: string;
  /** Absent when the counts read was refused; the chip then shows no number at all. */
  count?: number;
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={selected}
      className={cn(
        "inline-flex items-center gap-2 rounded-full px-3 py-2 transition",
        "shadow-[inset_0_0_0_1px_currentColor] hover:text-amber",
        selected ? "bg-amber-dim text-amber" : "bg-transparent text-line",
      )}
    >
      <span
        className={cn(
          "font-sans text-[13px] font-semibold",
          selected ? "text-amber" : "text-text",
        )}
      >
        {label}
      </span>
      {count !== undefined && (
        <span
          className={cn(
            "font-sans text-[13px] font-semibold",
            selected ? "text-amber" : "text-text3",
          )}
        >
          {count.toLocaleString()}
        </span>
      )}
    </button>
  );
}
