import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { FacetOption } from "../api/types";
import { useComboboxList } from "../lib/useComboboxList";

/**
 * One value out of a facet's vocabulary — the single-value sibling of {@link TagCombobox}, sharing its
 * open/active/blur mechanics so the two lists cannot close on different beats.
 *
 * <p>A picker rather than a `<select>` because these vocabularies are long: the universe names 148
 * industries, and finding one in a native dropdown means scrolling a list nobody can search. Typing
 * filters, which is how the Strategy filter offers the very same values.
 *
 * <p>A value the vocabulary does not carry is still shown and still kept. The plugin captures whatever
 * a page publishes, so a company can hold a sector Apollo never named — clearing it silently because
 * this list has no row for it would lose a fact nobody touched.
 */
export function FacetCombobox({
  listId,
  noun,
  value,
  options,
  onChange,
}: {
  listId: string;
  /** Plural, lower-case — it names the input, its placeholder and the clear button. */
  noun: string;
  /** The chosen wire value, or "" for nothing recorded. */
  value: string;
  /** The counted facets and the fixed country list are both offered here, so counts are not read. */
  options: readonly FacetOption[];
  onChange: (value: string) => void;
}) {
  // Null is "showing what is chosen"; a string is what the consultant is typing over it. Two states
  // in one field, because the box has to read as the answer when it is not being edited.
  const [query, setQuery] = useState<string | null>(null);

  const chosen = options.find((option) => option.value === value) ?? null;
  const shown = query ?? (chosen?.label ?? value);

  const needle = (query ?? "").trim().toLowerCase();
  const matches =
    query === null ? options : options.filter((option) => option.label.toLowerCase().includes(needle));

  const list = useComboboxList({
    optionCount: matches.length,
    onCommit: (index) => {
      const choice = matches[index];
      if (choice) {
        onChange(choice.value);
        setQuery(null);
      }
    },
  });

  const showList = list.open && matches.length > 0;
  const showEmpty = list.open && matches.length === 0;

  return (
    <div className="relative">
      <div
        className={cn(
          "flex h-[42px] items-center gap-2 rounded-lg border bg-panel2 px-3",
          list.open ? "border-sky" : "border-line",
        )}
      >
        <input
          role="combobox"
          aria-expanded={showList}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={showList ? `${listId}-${list.active}` : undefined}
          value={shown}
          placeholder={`Search ${noun}…`}
          onChange={(event) => {
            setQuery(event.target.value);
            list.setActive(0);
            list.setOpen(true);
          }}
          {...list.inputHandlers}
          // Selected on focus, so the first keystroke searches instead of appending to the answer
          // already in the box.
          onFocus={(event) => {
            list.inputHandlers.onFocus();
            event.target.select();
          }}
          // A half-typed query left behind would read as the chosen value while being nothing of the
          // kind. Leaving the field restores what is actually stored.
          onBlur={() => {
            list.inputHandlers.onBlur();
            setQuery(null);
          }}
          className="w-full bg-transparent font-mono text-[13px] text-text outline-none placeholder:text-text3"
        />

        {value !== "" && (
          <button
            type="button"
            aria-label={`Clear ${noun.replace(/s$/, "")}`}
            onMouseDown={(event) => {
              event.preventDefault();
              list.cancelBlur();
              onChange("");
              setQuery(null);
            }}
            className="flex-none text-text3 transition hover:text-text"
          >
            <Icon d={ICONS.close} size={12} />
          </button>
        )}
        <button
          type="button"
          aria-label={`${list.open ? "Hide" : "Show"} ${noun}`}
          // Toggled on mousedown: a click blurs the input first, and that timer would close the list
          // a beat after this reopened it.
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

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-lg border border-line bg-panel py-1 shadow-panel"
        >
          {matches.map((option, index) => (
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
              {option.value === value && (
                <Icon d={ICONS.check} size={12} className="ms-auto flex-none text-amber" />
              )}
            </li>
          ))}
        </ul>
      )}
      {showEmpty && (
        <div
          aria-live="polite"
          className="absolute z-20 mt-1 w-full rounded-lg border border-line bg-panel px-3 py-2 font-mono text-[12px] text-text3 shadow-panel"
        >
          No {noun} match that.
        </div>
      )}
    </div>
  );
}
