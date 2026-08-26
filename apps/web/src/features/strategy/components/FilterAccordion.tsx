import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { SelectionPill } from "./SelectionPill";

export interface SelectedTag {
  value: string;
  label: string;
}

/**
 * One accordion in the filter sidebar: a 52px header, and the panel's own controls beneath when open.
 *
 * <p>Open state changes the label's weight and nothing else. The row must not change height or
 * reflow when a panel opens, and an icon appearing beside the label shifts the text of every open
 * panel by its own width.
 *
 * <p>The pills summarise a <b>closed</b> panel, and sit on their own rows below the header: inside
 * it they had to be capped at two then `+N`, because three long industry names push the chevron off
 * a 300px rail.
 */
export function FilterAccordion({
  label,
  selected,
  tagTone = "sky",
  open,
  onToggleOpen,
  onReset,
  onRemove,
  children,
}: {
  label: string;
  selected: SelectedTag[];
  /** Off-limits reads in red; everything else is an ordinary selection. */
  tagTone?: "sky" | "red";
  open: boolean;
  onToggleOpen: () => void;
  onReset: () => void;
  onRemove?: (value: string) => void;
  children: ReactNode;
}) {
  const summarised = !open && selected.length > 0;

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
        className={cn(
          "flex min-h-[52px] w-full cursor-pointer items-center gap-2 px-4 py-3 text-left transition hover:bg-panel2",
          !summarised && "border-b border-line-soft",
        )}
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
          {summarised && (
            <button
              type="button"
              aria-label={`Clear ${label}`}
              onClick={(event) => {
                event.stopPropagation();
                onReset();
              }}
              className={cn(
                "flex flex-none items-center gap-1 rounded-full border border-line px-2 py-[3px] transition hover:border-text3",
                tagTone === "red" ? "text-red" : "text-text2",
              )}
            >
              <Icon d={ICONS.x} size={9} />
              <span className="font-sans text-[11px] font-semibold">{selected.length}</span>
            </button>
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
            d={open ? ICONS.chevronDown : ICONS.chevronRight}
            size={14}
            className="flex-none text-text3"
          />
        </span>
      </div>

      {summarised && (
        <div className="flex flex-wrap gap-[5px] border-b border-line-soft px-4 pb-3">
          {selected.map((tag) => (
            <SelectionPill
              key={tag.value}
              label={tag.label}
              tone={tagTone}
              onRemove={onRemove ? () => onRemove(tag.value) : undefined}
            />
          ))}
        </div>
      )}

      {open && <div className="border-b border-line-soft p-4">{children}</div>}
    </div>
  );
}
