import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";
import {
  clearFilter,
  filterSummary,
  isFilterInForce,
  type DataGridCheckFilter,
  type DataGridColumnFilter,
  type DataGridTextFilter,
} from "../../lib/gridFilters";
import { FilterCheckRow } from "./FilterCheckRow";
import { Popover } from "./Popover";
import { TOOLBAR_GHOST, ToolbarLabel } from "./Toolbar";

/** Past this many options a checkbox list gets a search box of its own. */
const CHECK_SEARCH_THRESHOLD = 8;

const FILTER_INPUT =
  "h-7 w-full min-w-0 rounded-[4px] border border-u-border-strong bg-u-surface px-2 font-sans text-[12px] text-u-text outline-none placeholder:text-u-text3 focus:border-u-accent";

const PANEL_FIELD =
  "flex h-7 w-full min-w-0 items-center justify-between gap-1.5 rounded-[4px] border border-u-border-strong bg-u-surface px-2 text-left font-sans text-[12px] text-u-text transition hover:border-u-text3 aria-expanded:border-u-accent";

const PANEL_TEXT_BUTTON =
  "inline-flex h-7 items-center gap-1.5 rounded-[6px] px-2 font-sans text-[12.5px] font-medium text-u-text2 transition hover:bg-u-raised hover:text-u-text disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent";

/** The editor for one filter: a text box, or a checkbox list. Shared by the header menu and the Filter panel. */
export function ColumnFilterEditor({
  filter,
  onEnter,
  focusOnMount = false,
}: {
  filter: DataGridColumnFilter;
  onEnter: () => void;
  focusOnMount?: boolean;
}) {
  return filter.kind === "check" ? (
    <CheckFilterList filter={filter} />
  ) : (
    <TextFilterInput filter={filter} onEnter={onEnter} focusOnMount={focusOnMount} />
  );
}

/** A text filter's box, with a ✕ inside it while it holds text; clearing keeps the caret in the box. */
function TextFilterInput({
  filter,
  onEnter,
  focusOnMount,
}: {
  filter: DataGridTextFilter;
  onEnter: () => void;
  focusOnMount: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (focusOnMount) inputRef.current?.focus();
  }, [focusOnMount]);
  return (
    <div className="relative min-w-0">
      <input
        ref={inputRef}
        type="text"
        value={filter.value}
        onChange={(event) => filter.onChange(event.target.value)}
        onKeyDown={(event) => {
          if (event.key === "Enter") onEnter();
        }}
        placeholder={filter.placeholder}
        aria-label={filter["aria-label"]}
        className={cn(FILTER_INPUT, filter.value && "pe-7")}
      />
      {filter.value && (
        <button
          type="button"
          onClick={() => {
            filter.onChange("");
            inputRef.current?.focus();
          }}
          aria-label="Clear filter"
          className="absolute end-1 top-1/2 grid size-5 -translate-y-1/2 flex-none place-items-center rounded text-u-text3 hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.close} size={12} />
        </button>
      )}
    </div>
  );
}

function CheckFilterList({ filter }: { filter: DataGridCheckFilter }) {
  const [query, setQuery] = useState("");
  const needle = query.trim().toLowerCase();
  const options = needle
    ? filter.options.filter((option) => option.label.toLowerCase().includes(needle))
    : filter.options;
  const toggle = (value: string) =>
    filter.onChange(
      filter.selected.includes(value)
        ? filter.selected.filter((selected) => selected !== value)
        : [...filter.selected, value],
    );
  return (
    <div className="flex flex-col gap-1">
      {filter.options.length > CHECK_SEARCH_THRESHOLD && (
        <input
          type="search"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search…"
          aria-label={`Search ${filter.label} options`}
          className={FILTER_INPUT}
        />
      )}
      <div role="group" aria-label={filter["aria-label"]} className="flex max-h-[220px] flex-col gap-0.5 overflow-y-auto">
        {options.map((option) => (
          <div key={option.value} className={option.indent && !needle ? "ps-4" : undefined}>
            <FilterCheckRow
              label={option.label}
              size="sm"
              checked={filter.selected.includes(option.value)}
              onToggle={() => toggle(option.value)}
            />
          </div>
        ))}
        {options.length === 0 && <span className="px-1 py-1.5 font-sans text-[12px] text-u-text3">No match</span>}
      </div>
    </div>
  );
}

/** One filter in the panel: column, operator, value, and its own ✕. A checkbox filter opens its list beneath. */
function FilterPanelRow({
  filter,
  expanded,
  focusOnMount,
  onToggleExpanded,
  onRemove,
  onEnter,
}: {
  filter: DataGridColumnFilter;
  expanded: boolean;
  focusOnMount: boolean;
  onToggleExpanded: () => void;
  onRemove: () => void;
  onEnter: () => void;
}) {
  return (
    <li className="flex flex-col gap-1.5">
      <div className="grid grid-cols-[88px_64px_minmax(0,1fr)_28px] items-center gap-1.5">
        <span className="truncate font-sans text-[12.5px] font-medium text-u-text">{filter.label}</span>
        <span className="truncate font-sans text-[12px] text-u-text3">
          {filter.kind === "check" ? "is any of" : "contains"}
        </span>
        {filter.kind === "check" ? (
          <button
            type="button"
            aria-expanded={expanded}
            aria-label={`${filter.label} values`}
            onClick={onToggleExpanded}
            className={PANEL_FIELD}
          >
            <span className={cn("truncate", filter.selected.length === 0 && "text-u-text3")}>
              {filter.selected.length > 0 ? filterSummary(filter) : "Choose…"}
            </span>
            <Icon d={ICONS.chevronDown} size={12} className="flex-none text-u-text3" />
          </button>
        ) : (
          <ColumnFilterEditor filter={filter} onEnter={onEnter} focusOnMount={focusOnMount} />
        )}
        <button
          type="button"
          aria-label={`Remove ${filter.label} filter`}
          onClick={onRemove}
          className="grid size-7 place-items-center rounded-[6px] text-u-text3 transition hover:bg-u-raised hover:text-u-text"
        >
          <Icon d={ICONS.close} size={13} />
        </button>
      </div>
      {filter.kind === "check" && expanded && (
        <div className="rounded-[6px] border border-u-border bg-u-bg p-1.5">
          <ColumnFilterEditor filter={filter} onEnter={onEnter} />
        </div>
      )}
    </li>
  );
}

/** The columns not yet in the panel, any one of which becomes a new filter line. */
function AddFilterMenu({
  columns,
  onAdd,
}: {
  columns: readonly [string, DataGridColumnFilter][];
  onAdd: (id: string) => void;
}) {
  return (
    <div role="group" aria-label="Choose a column to filter" className="mx-1.5 flex flex-col rounded-[6px] border border-u-border bg-u-bg p-1">
      {columns.map(([id, filter]) => (
        <button
          key={id}
          type="button"
          onClick={() => onAdd(id)}
          className="rounded-[5px] px-2 py-1.5 text-left font-sans text-[12.5px] text-u-text2 transition hover:bg-u-raised hover:text-u-text"
        >
          {filter.label}
        </button>
      ))}
    </div>
  );
}

/**
 * The toolbar's one place for a grid's filters: a plain Filter button while none is in force; a tinted
 * "Filter · N" with a ✕ that clears them all once any is. The panel lists every filter in force, adds one on any
 * column — a hidden one too — and clears them all. Renders nothing for a grid that offers no filters.
 */
export function GridFilterButton({
  filters,
  resultLabel,
}: {
  filters: Record<string, DataGridColumnFilter>;
  /** "86 of 128 companies", shown in the panel's heading while a filter is in force. */
  resultLabel?: string;
}) {
  const [added, setAdded] = useState<string[]>([]);
  const [expanded, setExpanded] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const [refocus, setRefocus] = useState<"trigger" | "add" | null>(null);
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const addButtonRef = useRef<HTMLButtonElement>(null);

  // A ✕ that clears or removes unmounts under the caret, so focus moves — once the render it caused has
  // landed, since Add filter is disabled until the removed column is addable again.
  useEffect(() => {
    if (!refocus) return;
    if (refocus === "add") addButtonRef.current?.focus();
    else wrapperRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
    setRefocus(null);
  }, [refocus]);

  const entries = Object.entries(filters);
  if (entries.length === 0) return null;

  const inForce = entries.filter(([, filter]) => isFilterInForce(filter));
  const shown = entries.filter(([id, filter]) => isFilterInForce(filter) || added.includes(id));
  const addable = entries.filter(([id]) => !shown.some(([shownId]) => shownId === id));
  const count = inForce.length;

  const clearAll = (then: "trigger" | "add") => {
    for (const [, filter] of entries) clearFilter(filter);
    setAdded([]);
    setExpanded(null);
    setAdding(false);
    setAnnouncement("All filters cleared");
    setRefocus(then);
  };

  const remove = (id: string, filter: DataGridColumnFilter) => {
    clearFilter(filter);
    setAdded((current) => current.filter((entry) => entry !== id));
    if (expanded === id) setExpanded(null);
    setAnnouncement(`${filter.label} filter removed`);
    setRefocus("add");
  };

  const add = (id: string) => {
    setAdded((current) => [...current, id]);
    setExpanded(id);
    setAdding(false);
  };

  const popover = (
    <Popover
      label={count > 0 ? `Filter, ${count} applied` : "Filter"}
      title="Filter"
      align="left"
      width={440}
      hasPopup="dialog"
      trigger={() => (
        <>
          <Icon d={ICONS.filter} size={14} className="flex-none" />
          <ToolbarLabel>Filter</ToolbarLabel>
          {count > 0 && (
            <span className="grid h-[18px] min-w-[18px] place-items-center rounded-[4px] bg-u-accent-solid px-[5px] font-sans text-[11px] font-bold text-white">
              {count}
            </span>
          )}
        </>
      )}
      triggerClassName={
        count > 0
          ? "inline-flex items-center gap-[7px] whitespace-nowrap px-2.5 font-sans text-body font-medium text-u-accent transition hover:bg-u-accent-tint"
          : TOOLBAR_GHOST
      }
    >
      {(close) => (
        <div className="flex flex-col gap-1">
          <div className="flex items-baseline justify-between gap-3 px-1.5 pb-1 pt-1">
            <span className="font-sans text-[13px] font-semibold text-u-text">Filters</span>
            {count > 0 && resultLabel && <span className="font-mono text-[11px] text-u-text3">{resultLabel}</span>}
          </div>

          {shown.length === 0 ? (
            <p className="px-1.5 py-2 font-sans text-[12.5px] text-u-text3">No filters applied.</p>
          ) : (
            <ul className="flex flex-col gap-1.5 px-1.5">
              {shown.map(([id, filter]) => (
                <FilterPanelRow
                  key={id}
                  filter={filter}
                  expanded={expanded === id}
                  focusOnMount={expanded === id}
                  onToggleExpanded={() => setExpanded(expanded === id ? null : id)}
                  onRemove={() => remove(id, filter)}
                  onEnter={close}
                />
              ))}
            </ul>
          )}

          {adding && addable.length > 0 && <AddFilterMenu columns={addable} onAdd={add} />}

          <div className="mt-1 flex items-center justify-between border-t border-u-border px-0.5 pt-2">
            <button
              ref={addButtonRef}
              type="button"
              aria-expanded={adding}
              disabled={addable.length === 0}
              onClick={() => setAdding((current) => !current)}
              className={PANEL_TEXT_BUTTON}
            >
              <Icon d={ICONS.plus} size={13} />
              Add filter
            </button>
            <button
              type="button"
              disabled={shown.length === 0}
              onClick={() => clearAll("add")}
              className={PANEL_TEXT_BUTTON}
            >
              Clear all
            </button>
          </div>
        </div>
      )}
    </Popover>
  );

  // One wrapper whichever state, so the first tick that puts a filter in force does not remount — and close — the panel.
  return (
    <span
      ref={wrapperRef}
      className={cn(
        "relative inline-flex h-[34px] flex-none items-stretch rounded-u-control",
        count > 0 && "overflow-hidden border border-u-accent-ring bg-u-accent-tint",
      )}
    >
      {popover}
      {count > 0 && (
        <>
          <span aria-hidden="true" className="w-px bg-u-accent-ring" />
          <button
            type="button"
            onClick={() => clearAll("trigger")}
            aria-label="Clear all filters"
            title="Clear all filters"
            className="grid w-[30px] place-items-center text-u-accent transition hover:bg-u-accent-tint"
          >
            <Icon d={ICONS.close} size={13} />
          </button>
        </>
      )}
      <span role="status" aria-live="polite" className="sr-only">
        {announcement}
      </span>
    </span>
  );
}
