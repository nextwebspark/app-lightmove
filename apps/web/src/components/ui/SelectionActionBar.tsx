import { useEffect, type ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * The floating bar a multi-select grid raises once something is ticked: how many are selected, what
 * can be done to them, and a way out.
 *
 * <p>The shape is the one every table-heavy product converged on — Gmail, Jira, Linear, ClickUp,
 * Adobe's Spectrum action bar, PatternFly's bulk selection — and the reasons it won are worth
 * keeping: it appears only when there is a selection, so it costs no chrome the rest of the time; it
 * floats near the rows rather than in the toolbar, so the cursor does not travel the height of the
 * screen between picking and acting; it states the count, because "Decline" over an unseen selection
 * is a question the user must be able to answer before pressing it; and it carries its own dismissal,
 * because a selection with no visible way to drop it is a trap.
 *
 * <p>Positioned against the grid it belongs to rather than the viewport — the caller gives it a
 * {@code relative} box — so it centres on the table instead of drifting off-centre by half the width
 * of the nav rail. The wrapper is click-through; only the bar itself takes the pointer, so the rows
 * it floats over stay usable.
 */
export function SelectionActionBar({
  count,
  noun,
  plural,
  onClear,
  children,
}: {
  count: number;
  /** What is selected, singular — "company". Only the screen reader hears it; the bar shows a count. */
  noun: string;
  /** Its plural, given rather than derived: English does not spell "companies" with an "s" on the end. */
  plural: string;
  onClear: () => void;
  /** The actions, as {@link SelectionAction} buttons. */
  children: ReactNode;
}) {
  // Escape is the way out of every other transient surface in the app, and a bar that ignored it
  // would leave the keyboard with only a tab-walk to the Clear button.
  useEffect(() => {
    const dismiss = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClear();
    };
    window.addEventListener("keydown", dismiss);
    return () => window.removeEventListener("keydown", dismiss);
  }, [onClear]);

  return (
    <div className="pointer-events-none absolute inset-x-0 bottom-4 z-30 flex justify-center px-3">
      <div
        role="region"
        aria-label={`${count} ${count === 1 ? noun : plural} selected`}
        className="animate-fade-up pointer-events-auto flex max-w-full flex-wrap items-center gap-x-1.5 gap-y-2 rounded-[10px] border border-line bg-panel px-2.5 py-2 shadow-panel"
      >
        <span aria-hidden className="whitespace-nowrap px-1.5 font-sans text-[13px] font-semibold text-text">
          <span className="text-amber">{count}</span> selected
        </span>
        <span aria-hidden className="mx-0.5 hidden h-5 w-px flex-none bg-line sm:block" />
        {children}
        <span aria-hidden className="mx-0.5 hidden h-5 w-px flex-none bg-line sm:block" />
        <button
          type="button"
          onClick={onClear}
          aria-label="Clear selection"
          title="Clear selection (Esc)"
          className="grid size-8 flex-none place-items-center rounded-[6px] text-text3 transition hover:bg-panel2 hover:text-text"
        >
          <Icon d={ICONS.close} size={14} />
        </button>
      </div>
    </div>
  );
}

/** One action in the bar. Icon plus label, because three destinations are not three glyphs anyone knows. */
export function SelectionAction({
  icon,
  label,
  onClick,
  disabled,
  tone = "neutral",
}: {
  icon: string;
  label: string;
  onClick: () => void;
  disabled?: boolean;
  /** `danger` tints the one action a user would not want to press by accident. */
  tone?: "neutral" | "danger";
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      className={cn(
        "inline-flex flex-none items-center gap-2 whitespace-nowrap rounded-[6px] px-3 py-2 font-sans text-[13px] font-medium transition disabled:opacity-40",
        tone === "danger"
          ? "text-red hover:bg-red-dim"
          : "text-text2 hover:bg-panel2 hover:text-text",
      )}
    >
      <Icon d={icon} size={14} className="flex-none" />
      {label}
    </button>
  );
}
