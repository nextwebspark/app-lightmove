import { useEffect, type ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";

/**
 * The pop-up a multi-select grid raises once something is ticked: the count, the actions and a way out,
 * at the bottom centre of the screen over a light scrim.
 *
 * <p>Fixed in place rather than portalled, so it stays inside a full-screen grid's panel. It sits just
 * under the drawer layer (90/95), so an opened row covers it, and clears the toast at the bottom. The
 * scrim only dims: it takes no pointer, so the rows under it can still be ticked.
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
    <>
      <div aria-hidden className="pointer-events-none fixed inset-0 z-[88] bg-u-scrim/50" />
      <div
        role="region"
        aria-label={`${count} ${count === 1 ? noun : plural} selected`}
        className="animate-fade-up fixed bottom-16 left-1/2 z-[89] flex max-w-[calc(100vw-24px)] -translate-x-1/2 flex-wrap items-center gap-x-1.5 gap-y-2 rounded-[10px] border border-u-border-strong bg-u-surface px-2.5 py-2 shadow-u-e3"
      >
        <span aria-hidden className="whitespace-nowrap px-1.5 font-sans text-[13px] font-semibold text-u-text">
          <span className="text-u-accent">{count}</span> selected
        </span>
        <span aria-hidden className="mx-0.5 hidden h-5 w-px flex-none bg-u-border-strong sm:block" />
        {children}
        <span aria-hidden className="mx-0.5 hidden h-5 w-px flex-none bg-u-border-strong sm:block" />
        <button
          type="button"
          onClick={onClear}
          aria-label="Clear selection"
          title="Clear selection (Esc)"
          className="grid size-8 flex-none place-items-center rounded-[6px] text-u-text3 transition hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.close} size={14} />
        </button>
      </div>
    </>
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
          ? "text-u-offlimits hover:bg-u-offlimits-tint"
          : "text-u-text2 hover:bg-u-raised hover:text-u-text",
      )}
    >
      <Icon d={icon} size={14} className="flex-none" />
      {label}
    </button>
  );
}
