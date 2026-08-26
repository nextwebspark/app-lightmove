import { cn } from "../../lib/cn";

/**
 * One checkbox row in a filter panel — the wireframe's shape for every axis with more than a handful
 * of values: Employees, Revenue, Market Segments.
 *
 * <p><b>Only the box changes when it is checked.</b> The label keeps the same weight, size and colour
 * either way, and the count stays muted. That is deliberate and it is what the wireframe does: a list
 * where selected rows also turn amber reads as two different kinds of row, and the eye has to
 * re-scan to find the checked ones. Leaving the text alone means the checkmarks are the only signal,
 * which is the one you actually want to follow down a column of eleven bands.
 *
 * <p>The count follows the current selection — every other axis applied, this one left out — so it
 * answers "how many are left if I tick this". The row's <i>position</i> does not follow it: order
 * comes from the universe read, so nothing slides out from under the hand mid-click.
 */
export function FilterCheckRow({
  label,
  count,
  checked,
  onToggle,
  size = "md",
}: {
  label: string;
  /** Omitted where the axis cannot be counted, or the counts read was refused. */
  count?: number;
  checked: boolean;
  onToggle: () => void;
  /** `sm` is the column picker's list; `md` is a band row on the filter rail. */
  size?: "sm" | "md";
}) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      onClick={onToggle}
      className={cn(
        "flex w-full items-center justify-between rounded-[5px] text-left transition hover:bg-panel2",
        size === "md" ? "px-1 py-2" : "px-1 py-[5px]",
      )}
    >
      <span className={cn("flex items-center", size === "md" ? "gap-3" : "gap-2")}>
        <CheckBox checked={checked} size={size} />
        <span
          className={cn(
            "font-sans text-text",
            size === "md" ? "text-[14px] font-medium" : "text-[12px] font-medium",
          )}
        >
          {label}
        </span>
      </span>
      {count !== undefined && (
        <span className="font-sans text-[13px] font-medium text-text3">
          {count.toLocaleString()}
        </span>
      )}
    </button>
  );
}

/**
 * The wireframe's square: an inset ring rather than a border, so ticking a row cannot shift the text
 * beside it by the half-pixel a border-width change would cost.
 */
export function CheckBox({
  checked,
  size = "md",
}: {
  checked: boolean;
  size?: "sm" | "md";
}) {
  const box = size === "md" ? "h-[18px] w-[18px]" : "h-[15px] w-[15px]";
  const glyph = size === "md" ? 10 : 9;

  return (
    <span
      className={cn(
        "grid flex-none place-items-center rounded-[4px] shadow-[inset_0_0_0_1.5px_currentColor]",
        box,
        checked ? "bg-amber-dim text-amber" : "text-line",
      )}
    >
      <svg
        width={glyph}
        height={glyph}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={3.2}
        className={cn("text-amber", checked ? "opacity-100" : "opacity-0")}
      >
        <path d="M20 6 9 17l-5-5" />
      </svg>
    </span>
  );
}
