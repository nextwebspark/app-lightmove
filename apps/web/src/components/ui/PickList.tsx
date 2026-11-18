import { useEffect, useId, useRef, useState, type KeyboardEvent } from "react";
import { cn } from "../../lib/cn";
import { Icon, ICONS } from "../layout/Icon";

export interface PickListOption {
  id: string;
  label: string;
}

/**
 * A popover's single-choice list in the status picker's look: one tab stop that takes focus as it opens, the arrows,
 * Home and End moving through the rows, a typed letter jumping to the next row it starts, Enter or Space choosing.
 */
export function PickList({
  label,
  options,
  value,
  onPick,
  emptyText = "Nothing to choose",
}: {
  label: string;
  options: readonly PickListOption[];
  value: string | null;
  onPick: (id: string) => void;
  emptyText?: string;
}) {
  const listId = useId();
  const listRef = useRef<HTMLDivElement>(null);
  const [activeId, setActiveId] = useState(value ?? options[0]?.id ?? null);
  const active = options.find((option) => option.id === activeId) ?? options[0] ?? null;

  useEffect(() => {
    listRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!active) return;
    listRef.current
      ?.querySelector<HTMLElement>(`#${CSS.escape(optionIdOf(listId, active.id))}`)
      ?.scrollIntoView?.({ block: "nearest" });
  }, [active, listId]);

  const moveTo = (index: number) => {
    if (options.length === 0) return;
    setActiveId(options[(index + options.length) % options.length].id);
  };
  const at = active ? options.indexOf(active) : -1;

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const step: Record<string, () => void> = {
      ArrowDown: () => moveTo(at + 1),
      ArrowUp: () => moveTo(at - 1),
      Home: () => moveTo(0),
      End: () => moveTo(options.length - 1),
      Enter: () => active && onPick(active.id),
      " ": () => active && onPick(active.id),
    };
    if (step[event.key]) {
      event.preventDefault();
      step[event.key]();
      return;
    }
    if (event.key.length === 1 && !event.metaKey && !event.ctrlKey && !event.altKey) {
      const letter = event.key.toLowerCase();
      const ordered = [...options.slice(at + 1), ...options.slice(0, at + 1)];
      const next = ordered.find((option) => option.label.toLowerCase().startsWith(letter));
      if (next) setActiveId(next.id);
    }
  };

  return (
    <div
      ref={listRef}
      id={listId}
      role="listbox"
      aria-label={label}
      tabIndex={0}
      aria-activedescendant={active ? optionIdOf(listId, active.id) : undefined}
      onKeyDown={onKeyDown}
      className="flex flex-col outline-none"
    >
      {options.map((option) => {
        const isHeld = option.id === value;
        return (
          <div
            key={option.id}
            id={optionIdOf(listId, option.id)}
            role="option"
            aria-selected={isHeld}
            onMouseEnter={() => setActiveId(option.id)}
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => onPick(option.id)}
            className={cn(
              "flex cursor-pointer items-center gap-2 rounded-u-control px-2 py-1.5 text-note text-u-text2",
              option.id === active?.id && "bg-u-raised text-u-text",
              isHeld && "font-medium text-u-text",
            )}
          >
            <span className="min-w-0 flex-1 truncate">{option.label}</span>
            {isHeld && <Icon d={ICONS.check} size={13} className="flex-none text-u-accent" />}
          </div>
        );
      })}
      {options.length === 0 && <p className="px-2 py-3 text-note text-u-text3">{emptyText}</p>}
    </div>
  );
}

function optionIdOf(listId: string, optionId: string): string {
  return `${listId}-${optionId}`;
}
