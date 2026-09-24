import { useState, type KeyboardEvent } from "react";
import { cn } from "../../lib/cn";
import { useComboboxList } from "../../lib/useComboboxList";
import { rankedFor, type ComboboxOption } from "./FacetCombobox";

/**
 * A short list typed as chips: Enter or a comma adds what is typed, × removes a chip, and Backspace in
 * an empty box takes the last one back. A repeat of an existing chip (any case) is not added twice.
 *
 * <p>Given `options`, typing also drops a list of them and a pick adds its label. Free text still
 * goes in: Enter takes a suggestion only when its label starts with what was typed or it was arrowed
 * to, so "Retail banking" is not swallowed by the "Banking" it happens to contain.
 */
export function TagListInput({
  values,
  onChange,
  placeholder,
  ariaLabel,
  maxItems,
  disabled,
  options,
  listId,
}: {
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  ariaLabel: string;
  maxItems?: number;
  disabled?: boolean;
  options?: readonly ComboboxOption[];
  /** Unique per rendered field when `options` is given — it wires the input to its own listbox. */
  listId?: string;
}) {
  const [draft, setDraft] = useState("");
  const isFull = maxItems !== undefined && values.length >= maxItems;
  const taken = new Set(values.map((value) => value.toLowerCase()));
  const needle = draft.trim().toLowerCase();
  const matches =
    options && needle ? rankedFor(needle, options).filter((option) => !taken.has(option.label.toLowerCase())) : [];

  const add = (value: string) => {
    setDraft("");
    if (!value || isFull) return;
    if (taken.has(value.toLowerCase())) return;
    onChange([...values, value]);
  };

  const list = useComboboxList({
    optionCount: matches.length,
    autoHighlightFirst: false,
    onCommit: (index) => {
      const choice = matches[index];
      if (choice) add(choice.label);
    },
  });

  const showList = list.open && matches.length > 0;

  const handleChange = (next: string) => {
    setDraft(next);
    const nextNeedle = next.trim().toLowerCase();
    const first = options && nextNeedle ? rankedFor(nextNeedle, options).find((option) => !taken.has(option.label.toLowerCase())) : undefined;
    list.setActive(first?.label.toLowerCase().startsWith(nextNeedle) ? 0 : -1);
    list.setOpen(true);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "Enter" && showList && list.active >= 0) {
      list.inputHandlers.onKeyDown(event);
      return;
    }
    if (event.key === "Enter" || event.key === ",") {
      event.preventDefault();
      add(draft.trim());
      list.setOpen(false);
      return;
    }
    if (event.key === "Backspace" && draft === "" && values.length > 0) {
      onChange(values.slice(0, -1));
      return;
    }
    list.inputHandlers.onKeyDown(event);
  };

  return (
    <div className="relative">
      <div className="flex min-h-[42px] w-full flex-wrap items-center gap-1.5 rounded-lg border border-u-border-strong bg-u-surface px-2 py-1.5 transition focus-within:border-u-accent">
        {values.map((value) => (
          <span
            key={value.toLowerCase()}
            className="inline-flex items-center gap-1 rounded-md bg-u-raised px-2 py-1 font-mono text-[12px] text-u-text ring-1 ring-u-border"
          >
            {value}
            {!disabled && (
              <button
                type="button"
                aria-label={`Remove ${value}`}
                onClick={() => onChange(values.filter((existing) => existing !== value))}
                className="text-u-text3 hover:text-u-offlimits"
              >
                ×
              </button>
            )}
          </span>
        ))}
        {!disabled && !isFull && (
          <input
            value={draft}
            aria-label={ariaLabel}
            placeholder={values.length === 0 ? placeholder : undefined}
            {...(options && {
              role: "combobox",
              "aria-expanded": showList,
              "aria-controls": listId,
              "aria-autocomplete": "list" as const,
              "aria-activedescendant": showList && list.active >= 0 ? `${listId}-${list.active}` : undefined,
            })}
            onChange={(event) => handleChange(event.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={list.inputHandlers.onFocus}
            onBlur={() => {
              list.inputHandlers.onBlur();
              add((showList && matches[list.active]?.label) || draft.trim());
            }}
            className="min-w-[120px] flex-1 bg-transparent px-1 py-1 font-mono text-[13px] text-u-text outline-none"
          />
        )}
      </div>

      {showList && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-56 w-full overflow-auto rounded-lg border border-u-border-strong bg-u-surface py-1 shadow-u-e3"
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
                "cursor-pointer truncate px-3 py-[7px] font-sans text-[13px] font-medium text-u-text",
                index === list.active && "bg-u-raised",
              )}
            >
              {option.label}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
