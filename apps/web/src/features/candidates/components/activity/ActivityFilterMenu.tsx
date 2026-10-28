import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { cn } from "../../../../lib/cn";
import { useEscapeKey } from "../../../../lib/useEscapeKey";

export interface FilterMenuOption {
  key: string;
  label: string;
  /** A second, quieter line: a colleague's role, the executive, Uncava. */
  sublabel?: string;
  /** The kind's tile or the person's face. */
  visual: ReactNode;
  /** How many lines it would show, given the other filter. */
  count: number;
}

/**
 * One of the Activity tab's two filters: a button naming what is applied, opening a menu of checkable options with
 * their counts. Several may be ticked; Only keeps one. A filter offering a single option is never a menu — it is a
 * plain label in the same place, so the row does not jump between people.
 */
export function ActivityFilterMenu({
  label,
  heading,
  clearLabel,
  options,
  selected,
  onChange,
  summary,
  footnote,
}: {
  /** The button's accessible name: "Filter by kind". */
  label: string;
  /** The menu's eyebrow: "Show", "Done by". */
  heading: string;
  /** Its reset: "Show all", "Everyone". */
  clearLabel: string;
  options: readonly FilterMenuOption[];
  selected: ReadonlySet<string>;
  onChange: (next: Set<string>) => void;
  /** What the button shows: the applied choice, or the resting label. */
  summary: ReactNode;
  footnote?: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const wrapper = useRef<HTMLDivElement>(null);
  const button = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const menuId = useId();

  const close = (returnFocus: boolean) => {
    setIsOpen(false);
    if (returnFocus) button.current?.focus();
  };
  useEscapeKey(isOpen, () => close(true));

  useEffect(() => {
    if (!isOpen) return;
    const handlePointer = (event: PointerEvent) => {
      if (!wrapper.current?.contains(event.target as Node)) setIsOpen(false);
    };
    document.addEventListener("pointerdown", handlePointer);
    return () => document.removeEventListener("pointerdown", handlePointer);
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || !menu.current) return;
    // Keep the menu inside the drawer: a second button near the edge would otherwise open it past the panel.
    const panel = menu.current.closest("[data-activity-root]") ?? document.body;
    const bounds = panel.getBoundingClientRect();
    const own = menu.current.getBoundingClientRect();
    const overflow = own.right - (bounds.right - 12);
    if (overflow > 0) menu.current.style.left = `${-overflow}px`;
  }, [isOpen]);

  if (options.length <= 1) {
    const only = options[0];
    return (
      <span
        className="inline-flex h-[30px] min-w-0 items-center gap-1.5 rounded-lg border border-u-border px-2 text-body text-u-text2"
        title={only ? `Only ${only.label} so far` : undefined}
      >
        {only?.visual}
        <span className="truncate">{only?.label ?? "Nothing yet"}</span>
      </span>
    );
  }

  const isFiltering = selected.size > 0;
  const toggle = (key: string) => {
    const next = new Set(selected);
    if (next.has(key)) next.delete(key);
    else next.add(key);
    onChange(next);
  };
  const handleMenuKeys = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== "ArrowDown" && event.key !== "ArrowUp") return;
    const items = [...(menu.current?.querySelectorAll<HTMLButtonElement>("[role=menuitemcheckbox]:not(:disabled)") ?? [])];
    if (items.length === 0) return;
    event.preventDefault();
    const at = items.indexOf(document.activeElement as HTMLButtonElement);
    items[(at + (event.key === "ArrowDown" ? 1 : -1) + items.length) % items.length].focus();
  };

  return (
    <div ref={wrapper} className="relative min-w-0">
      <button
        ref={button}
        type="button"
        aria-label={label}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        onClick={() => setIsOpen((open) => !open)}
        onKeyDown={(event) => {
          if (event.key === "ArrowDown" && !isOpen) {
            event.preventDefault();
            setIsOpen(true);
            requestAnimationFrame(() =>
              menu.current?.querySelector<HTMLButtonElement>("[role=menuitemcheckbox]:not(:disabled)")?.focus(),
            );
          }
        }}
        className={cn(
          "inline-flex h-[30px] max-w-full items-center gap-1.5 rounded-lg border px-2 text-body font-medium transition",
          isFiltering
            ? "border-u-accent-ring bg-u-accent-tint text-u-accent"
            : "border-u-border-strong bg-u-surface text-u-text hover:bg-u-raised",
          isOpen && "ring-3 ring-u-accent-tint",
        )}
      >
        <span className="flex min-w-0 items-center gap-1.5">{summary}</span>
        <span className={cn("flex-none transition-transform", isFiltering ? "text-u-accent" : "text-u-text3", isOpen && "rotate-180")}>
          <Icon d={ICONS.chevronDown} size={14} />
        </span>
      </button>
      {isOpen && (
        <div
          ref={menu}
          id={menuId}
          role="menu"
          aria-label={heading}
          onKeyDown={handleMenuKeys}
          className="absolute start-0 top-[calc(100%+6px)] z-30 w-[288px] max-w-[calc(100vw-32px)] animate-fade-up rounded-xl border border-u-border-strong bg-u-bg p-1.5 shadow-u-e3"
        >
          <div className="flex items-center justify-between px-2 pb-1.5 pt-1">
            <span className="type-micro-label text-u-text3">{heading}</span>
            {isFiltering && (
              <button
                type="button"
                onClick={() => onChange(new Set())}
                className="text-note font-medium text-u-accent hover:underline"
              >
                {clearLabel}
              </button>
            )}
          </div>
          {options.map((option) => {
            const isChecked = selected.has(option.key);
            return (
              <div key={option.key} className="group relative">
                <button
                  type="button"
                  role="menuitemcheckbox"
                  aria-checked={isChecked}
                  disabled={option.count === 0 && !isChecked}
                  onClick={() => toggle(option.key)}
                  className="grid w-full grid-cols-[15px_auto_minmax(0,1fr)_auto] items-center gap-2.5 rounded-lg px-2 py-1.5 text-start text-body text-u-text outline-none hover:bg-u-surface focus-visible:bg-u-surface focus-visible:ring-1 focus-visible:ring-u-accent-ring disabled:cursor-default disabled:opacity-40 disabled:hover:bg-transparent"
                >
                  <span
                    className={cn(
                      "grid size-[15px] place-items-center rounded-sm border-[1.5px]",
                      isChecked ? "border-transparent bg-u-accent-solid text-white" : "border-u-border-strong",
                    )}
                    aria-hidden="true"
                  >
                    {isChecked && <Icon d={ICONS.check} size={11} />}
                  </span>
                  {option.visual}
                  <span className="min-w-0">
                    <span className="block truncate">{option.label}</span>
                    {option.sublabel && <span className="block truncate text-meta text-u-text3">{option.sublabel}</span>}
                  </span>
                  <span className="min-w-[2ch] text-end font-mono text-meta text-u-text3 tabular-nums group-hover:invisible">
                    {option.count}
                  </span>
                </button>
                {option.count > 0 && (
                  <button
                    type="button"
                    onClick={() => onChange(new Set([option.key]))}
                    aria-label={`Only ${option.label}`}
                    className="absolute end-2 top-1/2 hidden -translate-y-1/2 rounded-md px-1.5 py-0.5 text-note font-medium text-u-accent hover:bg-u-accent-tint group-hover:block focus-visible:block"
                  >
                    Only
                  </button>
                )}
              </div>
            );
          })}
          {footnote && <p className="px-2 pb-1 pt-1.5 text-meta text-u-text3">{footnote}</p>}
        </div>
      )}
    </div>
  );
}
