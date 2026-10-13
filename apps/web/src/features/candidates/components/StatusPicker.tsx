import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { PipelineCatalog, PipelineStage, PipelineStatus } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import { findStatus, pipelineSwatchClass, statusStyle } from "../lib/pipelineStatus";

type StatusPickerSize = "sm" | "md";

const TRIGGER_SIZE: Record<StatusPickerSize, string> = {
  sm: "h-7 gap-1.5 px-2 text-meta",
  md: "h-8 gap-2 px-2.5 text-note",
};

interface StageGroup {
  stage: PipelineStage;
  statuses: PipelineStatus[];
}

/**
 * Every status picker: a button that reads as the status held, opening the workspace's statuses under
 * their stages with a box that searches them. Typing only narrows the list — it never makes a status.
 * Retired ones are left out, except the one the candidate holds now.
 */
export function StatusPicker({
  value,
  onChange,
  currentLabel,
  disabled,
  size = "md",
  className,
  "aria-label": ariaLabel,
}: {
  value: string;
  onChange: (statusKey: string) => void;
  /** The row's own label for a status the catalog no longer lists. */
  currentLabel?: string | null;
  disabled?: boolean;
  size?: StatusPickerSize;
  className?: string;
  "aria-label": string;
}) {
  const { catalog } = usePipelineCatalog();
  const held = statusStyle(value, { statusLabel: currentLabel }, catalog);
  const heldColour = findStatus(catalog, value)?.status.colour ?? held.colour;

  return (
    <Popover
      label={`${ariaLabel}: ${held.label}`}
      hasPopup="listbox"
      placement="auto"
      width={280}
      disabled={disabled}
      triggerClassName={cn(
        "inline-flex min-w-0 max-w-full items-center rounded-u-control border border-u-border-strong bg-u-surface font-medium text-u-text transition hover:border-u-text3 disabled:cursor-not-allowed disabled:opacity-40",
        TRIGGER_SIZE[size],
        className,
      )}
      trigger={(open) => (
        <>
          <span aria-hidden className={cn("size-[7px] flex-none rounded-full", pipelineSwatchClass(heldColour))} />
          <span className="min-w-0 flex-1 truncate text-start">{held.label}</span>
          <Icon
            d={ICONS.chevronDown}
            size={12}
            className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
          />
        </>
      )}
    >
      {(close) => (
        <StatusList
          catalog={catalog}
          value={value}
          label={ariaLabel}
          onPick={(statusKey) => {
            close();
            if (statusKey !== value) onChange(statusKey);
          }}
        />
      )}
    </Popover>
  );
}

function StatusList({
  catalog,
  value,
  label,
  onPick,
}: {
  catalog: PipelineCatalog;
  value: string;
  label: string;
  onPick: (statusKey: string) => void;
}) {
  const listId = useId();
  const [search, setSearch] = useState("");
  const groups = useMemo(() => matchingGroups(catalog, value, search), [catalog, value, search]);
  const offered = useMemo(() => groups.flatMap((group) => group.statuses), [groups]);
  const [activeKey, setActiveKey] = useState(value);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const active = offered.find((status) => status.key === activeKey) ?? offered[0] ?? null;

  useEffect(() => {
    inputRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!active) return;
    const row = listRef.current?.querySelector<HTMLElement>(`#${CSS.escape(optionId(listId, active.key))}`);
    row?.scrollIntoView?.({ block: "nearest" });
  }, [active, listId]);

  const move = (step: number) => {
    if (offered.length === 0) return;
    const at = active ? offered.indexOf(active) : -1;
    setActiveKey(offered[(at + step + offered.length) % offered.length].key);
  };

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      move(1);
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      move(-1);
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (active) onPick(active.key);
    }
  };

  return (
    <div className="flex flex-col gap-1.5">
      <label className="flex items-center gap-2 rounded-u-control border border-u-border-strong bg-u-sunken px-2.5 py-1.5 focus-within:border-u-accent">
        <Icon d={ICONS.search} size={13} className="flex-none text-u-text3" />
        <input
          ref={inputRef}
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          onKeyDown={onKeyDown}
          placeholder="Search statuses"
          role="combobox"
          aria-label={`Search statuses for ${label}`}
          aria-expanded
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={active ? optionId(listId, active.key) : undefined}
          className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
        />
      </label>

      <div ref={listRef} id={listId} role="listbox" aria-label={label} className="flex flex-col">
        {groups.map((group) => (
          <div key={group.stage.id} role="group" aria-label={group.stage.label} className="pt-2 first:pt-1">
            <div className="flex items-center gap-2 px-2 pb-1">
              <span
                aria-hidden
                className={cn("size-2 flex-none rounded-full", pipelineSwatchClass(group.stage.colour))}
              />
              <span className="text-note font-semibold text-u-text">{group.stage.label}</span>
              <span className="font-mono text-eyebrow text-u-text3">{group.statuses.length}</span>
            </div>
            <div className="ms-3 flex flex-col border-s border-u-border-strong ps-2">
              {group.statuses.map((status) => {
                const isHeld = status.key === value;
                const isActive = status.key === active?.key;
                return (
                  <div
                    key={status.id}
                    id={optionId(listId, status.key)}
                    role="option"
                    aria-selected={isHeld}
                    onMouseEnter={() => setActiveKey(status.key)}
                    onMouseDown={(event) => event.preventDefault()}
                    onClick={() => onPick(status.key)}
                    className={cn(
                      "flex cursor-pointer items-center gap-2 rounded-u-control px-2 py-1.5 text-note text-u-text2",
                      isActive && "bg-u-raised text-u-text",
                      isHeld && "font-medium text-u-text",
                    )}
                  >
                    <span className="min-w-0 flex-1 truncate">{status.label}</span>
                    {isHeld && <Icon d={ICONS.check} size={13} className="flex-none text-u-accent" />}
                  </div>
                );
              })}
            </div>
          </div>
        ))}
        {offered.length === 0 && <p className="px-2 py-3 text-note text-u-text3">No status matches</p>}
      </div>
    </div>
  );
}

function optionId(listId: string, statusKey: string): string {
  return `${listId}-${statusKey}`;
}

function matchingGroups(catalog: PipelineCatalog, held: string, search: string): StageGroup[] {
  const needle = search.trim().toLowerCase();
  const groups: StageGroup[] = catalog.stages
    .map((stage) => ({
      stage,
      statuses: stage.statuses.filter((status) => (!status.retired && !stage.retired) || status.key === held),
    }))
    .filter((group) => group.statuses.length > 0);
  if (!needle) return groups;
  return groups
    .map((group) => ({
      stage: group.stage,
      statuses: group.stage.label.toLowerCase().includes(needle)
        ? group.statuses
        : group.statuses.filter((status) => status.label.toLowerCase().includes(needle)),
    }))
    .filter((group) => group.statuses.length > 0);
}
