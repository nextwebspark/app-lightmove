import { cn } from "../../lib/cn";

/**
 * One checkbox row in a filter panel. Only the box changes when checked, so the checkmarks are the one signal;
 * the count is over the whole universe and does not move as boxes are ticked.
 */
export function FilterCheckRow({
  label,
  count,
  checked,
  onToggle,
  size = "md",
}: {
  label: string;
  /** Omitted where the universe cannot count the axis; the row then shows nothing on the right. */
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
      className="flex w-full items-center justify-between rounded-[5px] px-1 py-[5px] text-left transition hover:bg-u-raised"
    >
      <span className={cn("flex items-center", size === "md" ? "gap-2.5" : "gap-2")}>
        <CheckBox checked={checked} size={size} />
        <span
          className={cn(
            "font-sans text-u-text",
            size === "md" ? "text-[13px] font-medium" : "text-[12px] font-medium",
          )}
        >
          {label}
        </span>
      </span>
      {count !== undefined && (
        <span className="font-sans text-[12px] font-medium text-u-text3">
          {count.toLocaleString()}
        </span>
      )}
    </button>
  );
}

/**
 * The wireframe's square: an inset ring rather than a border, so ticking a row cannot shift the text
 * beside it by the half-pixel a border-width change would cost.
 *
 * <p>Purely visual — no role and no handler of its own. The thing that is actually checkable is the
 * control around it, which is a filter row here and a grid checkbox on the selectable tables.
 */
export function CheckBox({
  checked,
  size = "md",
}: {
  /** `"mixed"` is a select-all box over a partial selection — a dash, not a tick. */
  checked: boolean | "mixed";
  size?: "sm" | "md";
}) {
  const box = size === "md" ? "h-[16px] w-[16px]" : "h-[15px] w-[15px]";
  const glyph = 9;

  return (
    <span
      className={cn(
        "grid flex-none place-items-center rounded-[4px] shadow-[inset_0_0_0_1.5px_currentColor]",
        box,
        checked ? "bg-u-accent-tint text-u-accent" : "text-u-border-strong",
      )}
    >
      <svg
        width={glyph}
        height={glyph}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth={3.2}
        className={cn("text-u-accent", checked ? "opacity-100" : "opacity-0")}
      >
        <path d={checked === "mixed" ? "M5 12h14" : "M20 6 9 17l-5-5"} />
      </svg>
    </span>
  );
}
