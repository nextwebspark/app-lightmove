import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { useComboboxList } from "../lib/useComboboxList";
import { SelectionPill } from "./SelectionPill";

interface ComboboxOption {
  value: string;
  label: string;
  count?: number;
}

/**
 * A tag box: what is taken sits as pills above an input that drops a list of what else can be. The
 * caller filters and orders `options`, and hears every keystroke so a server-backed one can debounce.
 */
export function TagCombobox({
  listId,
  noun,
  tags,
  options,
  emptyText,
  isStale = false,
  onQueryChange,
  onPick,
  onRemove,
}: {
  listId: string;
  /** Plural, lower-case — it names the input, its placeholder and the chevron. */
  noun: string;
  tags: { value: string; label: string }[];
  options: ComboboxOption[];
  /** What an exhausted list says, or null while the caller cannot yet tell that it is exhausted. */
  emptyText: string | null;
  /** The rows still answer an older query, so they may be taken by neither Enter nor belief. */
  isStale?: boolean;
  onQueryChange?: (query: string) => void;
  onPick: (value: string) => void;
  onRemove: (value: string) => void;
}) {
  const [query, setQuery] = useState("");

  const handleQueryChange = (next: string) => {
    setQuery(next);
    onQueryChange?.(next);
  };

  const list = useComboboxList({
    // Stale rows answer the previous keystroke; Enter must not take one.
    optionCount: isStale ? 0 : options.length,
    onCommit: (index) => {
      const choice = options[index];
      if (choice) {
        onPick(choice.value);
        handleQueryChange("");
      }
    },
  });

  const showList = list.open && options.length > 0;
  const showEmpty = list.open && !isStale && options.length === 0 && emptyText !== null;

  return (
    <div className="relative">
      <div className="rounded-md border border-line bg-panel2">
        {tags.length > 0 && (
          <div className="flex flex-wrap gap-[5px] px-2 pt-2">
            {tags.map((tag) => (
              <SelectionPill
                key={tag.value}
                label={tag.label}
                tone="amber"
                onRemove={() => onRemove(tag.value)}
              />
            ))}
          </div>
        )}
        <div className="flex h-9 items-center gap-2 px-[10px]">
          <Icon d={ICONS.search} size={13} className="flex-none text-text3" />
          <input
            role="combobox"
            aria-expanded={showList}
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={showList ? `${listId}-${list.active}` : undefined}
            value={query}
            placeholder={`Search ${noun}…`}
            aria-label={`Search ${noun}`}
            onChange={(event) => {
              handleQueryChange(event.target.value);
              list.setActive(0);
              list.setOpen(true);
            }}
            {...list.inputHandlers}
            className="w-full bg-transparent font-sans text-[12px] font-medium text-text outline-none placeholder:text-text3"
          />
          <button
            type="button"
            aria-label={`${list.open ? "Hide" : "Show"} ${noun}`}
            // Toggled on mousedown: a click blurs the input first, and that timer would close the
            // list a beat after this reopened it.
            onMouseDown={(event) => {
              event.preventDefault();
              list.cancelBlur();
              list.setOpen(!list.open);
            }}
            className="flex-none text-text3 transition hover:text-text"
          >
            <Icon
              d={ICONS.chevronDown}
              size={13}
              className={cn("transition", list.open ? "rotate-180" : "")}
            />
          </button>
        </div>
      </div>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-10 mt-1 max-h-64 w-full overflow-auto rounded-md border border-line bg-panel py-1 shadow-panel"
        >
          {options.map((option, index) => (
            <li
              key={option.value}
              id={`${listId}-${index}`}
              role="option"
              aria-selected={index === list.active}
              onMouseDown={(event) => list.commitFromPointer(event, index)}
              onMouseEnter={() => list.setActive(index)}
              className={cn(
                "flex cursor-pointer items-center gap-2 px-3 py-[7px]",
                index === list.active ? "bg-panel2" : "",
              )}
            >
              <span className="truncate font-sans text-[13px] font-medium text-text">
                {option.label}
              </span>
              {option.count !== undefined && (
                <span className="ms-auto flex-none font-sans text-[11px] text-text3">
                  {option.count.toLocaleString()}
                </span>
              )}
            </li>
          ))}
        </ul>
      )}
      {showEmpty && (
        <div
          aria-live="polite"
          className="absolute z-10 mt-1 w-full rounded-md border border-line bg-panel px-3 py-2 font-sans text-[12px] text-text3 shadow-panel"
        >
          {emptyText}
        </div>
      )}
    </div>
  );
}
