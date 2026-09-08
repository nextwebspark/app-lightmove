import { cn } from "../../../lib/cn";

/**
 * One selectable value as a pill — the wireframe's Location panel, and only that panel.
 *
 * <p>Pills are right for a country list this short, where every value fits on a few rows and
 * the shape of the set is the information. They are wrong for eleven headcount bands or twenty
 * sector groups, which is why every other panel is a checkbox list: an ordered axis read as wrapped
 * pills loses the order, and a long list of pills is a wall rather than a filter.
 *
 * <p>The chip carries no count. The universe is a handful of countries deep on this axis, so a
 * count decides nothing a consultant is choosing between — and counting them meant the panel could
 * not draw until the facets read came back.
 */
export function FilterChip({
  label,
  selected,
  onToggle,
}: {
  label: string;
  selected: boolean;
  onToggle: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={selected}
      className={cn(
        "inline-flex items-center rounded-full px-[10px] py-[4px] transition",
        "shadow-[inset_0_0_0_1px_currentColor] hover:text-amber",
        selected ? "bg-amber-dim text-amber" : "bg-transparent text-line",
      )}
    >
      <span
        className={cn(
          "font-sans text-[12px] font-medium",
          selected ? "text-amber" : "text-text",
        )}
      >
        {label}
      </span>
    </button>
  );
}
