import type { ReactNode } from "react";
import { Icon } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/** Chevron down when open, right when closed — the wireframe's two paths. */
const CHEVRON_OPEN = "m6 9 6 6 6-6";
const CHEVRON_CLOSED = "m9 18 6-6-6-6";

/**
 * One accordion in the filter sidebar: a 52px header, and the panel's own controls beneath when open.
 *
 * <p>Open state changes the label's weight and nothing else — same size, same colour, no icon. The
 * row must not change height or reflow when a panel opens, and an icon appearing beside the label
 * shifts the text of every open panel by its own width.
 *
 * <p>The other two things swap on state. The selected-value tags show only when the panel is
 * <b>closed</b> — they exist to say what a collapsed panel is doing, and repeating them above the
 * controls that set them would be noise. Reset appears only when open, for the same reason: it
 * belongs to the controls, not to the summary.
 *
 * <p>The tag preview is capped at two then `+N`. That is a real constraint in a 300px rail, not a
 * stylistic one — three long industry names would push the chevron off the row — and a header that
 * only said "3 selected" would force you to open it to find out which three.
 */
export function FilterAccordion({
  label,
  selectedValues,
  tagTone = "sky",
  open,
  onToggleOpen,
  onReset,
  children,
}: {
  label: string;
  selectedValues: string[];
  /** Off-limits counts in red; everything else reads as an ordinary selection. */
  tagTone?: "sky" | "red";
  open: boolean;
  onToggleOpen: () => void;
  onReset: () => void;
  children: ReactNode;
}) {
  const preview = selectedValues.slice(0, 2);
  const overflow = selectedValues.length - preview.length;
  const tagClass =
    tagTone === "red" ? "bg-red-dim text-red" : "bg-sky-dim text-sky";

  return (
    <div>
      <div
        role="button"
        tabIndex={0}
        aria-expanded={open}
        onClick={onToggleOpen}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            onToggleOpen();
          }
        }}
        className="flex min-h-[52px] w-full cursor-pointer items-center gap-2 border-b border-line-soft px-4 py-3 text-left transition hover:bg-panel2"
      >
        <span
          className={cn(
            "whitespace-nowrap font-sans text-[13px] text-text",
            open ? "font-bold" : "font-medium",
          )}
        >
          {label}
        </span>
        <span className="ml-auto flex items-center gap-2 overflow-hidden">
          {!open && selectedValues.length > 0 && (
            <span className="flex gap-1 overflow-hidden">
              {preview.map((value) => (
                <span
                  key={value}
                  className={cn(
                    "whitespace-nowrap rounded-[4px] px-[5px] py-[2px] font-sans text-[10px] font-bold",
                    tagClass,
                  )}
                >
                  {value}
                </span>
              ))}
              {overflow > 0 && (
                <span
                  className={cn(
                    "whitespace-nowrap rounded-[4px] px-[5px] py-[2px] font-sans text-[10px] font-bold",
                    tagClass,
                  )}
                >
                  +{overflow}
                </span>
              )}
            </span>
          )}
          {open && (
            <button
              type="button"
              onClick={(event) => {
                // The Reset button lives inside the header, which is itself the open/close control.
                event.stopPropagation();
                onReset();
              }}
              className="flex-none px-1 py-[2px] font-sans text-[11px] font-semibold text-text3 transition hover:text-text"
            >
              Reset
            </button>
          )}
          <Icon
            d={open ? CHEVRON_OPEN : CHEVRON_CLOSED}
            size={14}
            className="flex-none text-text3"
          />
        </span>
      </div>

      {open && <div className="border-b border-line-soft p-4">{children}</div>}
    </div>
  );
}
