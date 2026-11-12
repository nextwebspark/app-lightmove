import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent, type ReactNode } from "react";
import { cn } from "../../lib/cn";
import { Icon, ICONS } from "../layout/Icon";
import { Popover } from "./Popover";

export interface ListboxOption<T extends string> {
  value: T;
  label: string;
  /** A quieter second part of the row, after the label ("· DD MMM YYYY"). */
  detail?: string;
  /** More words a search matches beyond the label and detail (a zone's id, its countries). */
  keywords?: string;
}

export interface ListboxGroup<T extends string> {
  label?: string;
  options: ListboxOption<T>[];
}

/**
 * A select drawn as the status and currency pickers are: a button reading as the choice held, opening a portalled
 * list with a check on it, the arrow keys and Enter, and — where `searchable` — a box that narrows the list.
 */
export function ListboxSelect<T extends string>({
  value,
  onChange,
  options,
  searchable = false,
  searchPlaceholder = "Search",
  noMatch = "Nothing matches",
  invalid,
  disabled,
  width = 300,
  className,
  "aria-label": ariaLabel,
}: {
  value: T;
  onChange: (value: T) => void;
  /** A flat list, or groups under their own headings. */
  options: ListboxOption<T>[] | ListboxGroup<T>[];
  searchable?: boolean;
  searchPlaceholder?: string;
  noMatch?: string;
  invalid?: boolean;
  disabled?: boolean;
  width?: number;
  className?: string;
  "aria-label": string;
}) {
  const groups = useMemo(() => groupsOf(options), [options]);
  const held = useMemo(
    () => groups.flatMap((group) => group.options).find((option) => option.value === value),
    [groups, value],
  );

  return (
    <Popover
      label={ariaLabel}
      hasPopup="listbox"
      placement="auto"
      width={width}
      disabled={disabled}
      triggerClassName={cn(
        "flex w-full min-w-0 items-center gap-2 rounded-[6px] border bg-u-raised px-3 py-2.5 text-[13px] text-u-text",
        "outline-none transition hover:border-u-text3 focus-visible:border-u-accent disabled:opacity-60",
        invalid ? "border-u-offlimits" : "border-u-border-strong",
        className,
      )}
      trigger={(open) => (
        <>
          <span className="flex min-w-0 flex-1 items-baseline gap-1.5 text-start">
            {held ? <OptionText option={held} /> : <span className="truncate text-u-text3">—</span>}
          </span>
          <Icon
            d={ICONS.chevronDown}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
          />
        </>
      )}
    >
      {(close) => (
        <OptionList
          value={value}
          groups={groups}
          searchable={searchable}
          searchPlaceholder={searchPlaceholder}
          noMatch={noMatch}
          label={ariaLabel}
          onPick={(picked) => {
            close();
            if (picked !== value) onChange(picked);
          }}
        />
      )}
    </Popover>
  );
}

function OptionText<T extends string>({ option }: { option: ListboxOption<T> }): ReactNode {
  return (
    <>
      <span className={option.detail ? "flex-none" : "min-w-0 truncate"}>{option.label}</span>
      {option.detail && <span className="min-w-0 truncate font-mono text-meta text-u-text3">{option.detail}</span>}
    </>
  );
}

function OptionList<T extends string>({
  value,
  groups,
  searchable,
  searchPlaceholder,
  noMatch,
  label,
  onPick,
}: {
  value: T;
  groups: ListboxGroup<T>[];
  searchable: boolean;
  searchPlaceholder: string;
  noMatch: string;
  label: string;
  onPick: (value: T) => void;
}) {
  const listId = useId();
  const [search, setSearch] = useState("");
  const shown = useMemo(() => matching(groups, search), [groups, search]);
  const offered = useMemo(() => shown.flatMap((group) => group.options.map((option) => option.value)), [shown]);
  const [activeValue, setActiveValue] = useState<T>(value);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const active = offered.includes(activeValue) ? activeValue : (offered[0] ?? null);

  useEffect(() => {
    (searchable ? inputRef.current : listRef.current)?.focus();
  }, [searchable]);

  useEffect(() => {
    if (active === null) return;
    const row = listRef.current?.querySelector<HTMLElement>(`#${CSS.escape(optionId(listId, active))}`);
    row?.scrollIntoView?.({ block: "nearest" });
  }, [active, listId]);

  const move = (step: number) => {
    if (offered.length === 0) return;
    const at = active === null ? -1 : offered.indexOf(active);
    setActiveValue(offered[(at + step + offered.length) % offered.length]);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      move(1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      move(-1);
    } else if (event.key === "Home" && !searchable) {
      event.preventDefault();
      if (offered.length) setActiveValue(offered[0]);
    } else if (event.key === "End" && !searchable) {
      event.preventDefault();
      if (offered.length) setActiveValue(offered[offered.length - 1]);
    } else if (event.key === "Enter" || (event.key === " " && !searchable)) {
      event.preventDefault();
      if (active !== null) onPick(active);
    }
  };

  const row = (option: ListboxOption<T>) => {
    const isHeld = option.value === value;
    return (
      <div
        key={option.value || "none"}
        id={optionId(listId, option.value)}
        role="option"
        aria-selected={isHeld}
        aria-label={option.detail ? `${option.label} ${option.detail}` : option.label}
        onMouseEnter={() => setActiveValue(option.value)}
        onMouseDown={(event) => event.preventDefault()}
        onClick={() => onPick(option.value)}
        className={cn(
          "flex cursor-pointer items-baseline gap-2 rounded-u-control px-2 py-1.5 text-note text-u-text2",
          option.value === active && "bg-u-raised text-u-text",
          isHeld && "font-medium text-u-text",
        )}
      >
        <span className="min-w-0 flex-1 truncate">
          {option.label}
          {option.detail && <span className="ms-1.5 font-mono text-meta font-normal text-u-text3">{option.detail}</span>}
        </span>
        {isHeld && <Icon d={ICONS.check} size={13} className="flex-none self-center text-u-accent" />}
      </div>
    );
  };

  return (
    <div className="flex flex-col gap-1.5">
      {searchable && (
        <label className="flex items-center gap-2 rounded-u-control border border-u-border-strong bg-u-sunken px-2.5 py-1.5 focus-within:border-u-accent">
          <Icon d={ICONS.search} size={13} className="flex-none text-u-text3" />
          <input
            ref={inputRef}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            onKeyDown={onKeyDown}
            placeholder={searchPlaceholder}
            role="combobox"
            aria-label={`Search ${label.toLowerCase()}`}
            aria-expanded
            aria-controls={listId}
            aria-autocomplete="list"
            aria-activedescendant={active === null ? undefined : optionId(listId, active)}
            className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
          />
        </label>
      )}

      <div
        ref={listRef}
        id={listId}
        role="listbox"
        aria-label={label}
        tabIndex={searchable ? undefined : -1}
        onKeyDown={searchable ? undefined : onKeyDown}
        aria-activedescendant={searchable || active === null ? undefined : optionId(listId, active)}
        className="flex flex-col outline-none"
      >
        {shown.map((group, index) =>
          group.label ? (
            <div key={group.label} role="group" aria-label={group.label} className="pt-2 first:pt-1">
              <div className="px-2 pb-1 type-micro-label font-mono text-u-text3">{group.label}</div>
              {group.options.map(row)}
            </div>
          ) : (
            <div key={`group-${index}`} role="presentation">
              {group.options.map(row)}
            </div>
          ),
        )}
        {offered.length === 0 && <p className="px-2 py-3 text-note text-u-text3">{noMatch}</p>}
      </div>
    </div>
  );
}

function groupsOf<T extends string>(options: ListboxOption<T>[] | ListboxGroup<T>[]): ListboxGroup<T>[] {
  if (options.length === 0) return [];
  return "options" in options[0] ? (options as ListboxGroup<T>[]) : [{ options: options as ListboxOption<T>[] }];
}

/** Every word typed must appear somewhere in the option's label, detail or keywords, accents and case aside. */
export function matching<T extends string>(groups: ListboxGroup<T>[], search: string): ListboxGroup<T>[] {
  const words = folded(search).split(/\s+/).filter(Boolean);
  if (words.length === 0) return groups;
  return groups
    .map((group) => ({
      ...group,
      options: group.options.filter((option) => {
        const text = folded(`${option.label} ${option.detail ?? ""} ${option.keywords ?? ""}`);
        return words.every((word) => text.includes(word));
      }),
    }))
    .filter((group) => group.options.length > 0);
}

function folded(text: string): string {
  return text.normalize("NFD").replace(/[̀-ͯ]/g, "").toLowerCase();
}

function optionId(listId: string, value: string): string {
  return `${listId}-${value.replace(/[^A-Za-z0-9_-]/g, "_") || "none"}`;
}
