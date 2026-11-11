import { cn } from "../../../lib/cn";

/**
 * One value as a pill, for the Location panel only (other axes are too long or ordered for pills). No count:
 * it decides nothing and kept the panel waiting on the facets read.
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
        "shadow-[inset_0_0_0_1px_currentColor] hover:text-u-accent",
        selected ? "bg-u-accent-tint text-u-accent" : "bg-transparent text-u-border-strong",
      )}
    >
      <span
        className={cn(
          "font-sans text-[12px] font-medium",
          selected ? "text-u-accent" : "text-u-text",
        )}
      >
        {label}
      </span>
    </button>
  );
}
