import { useMemo, useRef, useState } from "react";
import type { PipelineCatalog } from "../../api/types";
import {
  colourDotClass,
  findStatus,
  pickableGroups,
  tagsOutOfProcess,
} from "../../domain/pipelineStatus";
import { cn } from "../lib/cn";
import { ICONS } from "../lib/icons";
import { useDismiss } from "../lib/useDismiss";
import { Icon } from "./Icon";

/**
 * The workspace's own pipeline as one field: the status and its stage, opening onto every live stage with its statuses
 * — a way out marked Out of process, the starting status named — as the web app's StatusPicker offers them.
 */
export function StatusPicker({
  catalog,
  value,
  startingKey,
  onChange,
  disabled = false,
}: {
  catalog: PipelineCatalog | undefined;
  value: string | null;
  startingKey: string | null;
  onChange: (key: string) => void;
  disabled?: boolean;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  useDismiss(containerRef, isOpen, () => setIsOpen(false));
  const current = catalog && value ? findStatus(catalog, value) : null;
  const groups = useMemo(() => (catalog ? pickableGroups(catalog, query, value) : []), [catalog, query, value]);

  return (
    <div ref={containerRef} className="relative">
      <button
        type="button"
        onClick={() => setIsOpen((open) => !open)}
        disabled={disabled || !catalog}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        aria-label={current ? `Status: ${current.status.label}` : "Status"}
        className={cn(
          "flex w-full items-center gap-2 rounded-[8px] border bg-u-raised px-2.5 py-2 text-start transition hover:border-u-text3 disabled:opacity-60",
          isOpen ? "border-u-accent" : "border-u-border-strong",
        )}
      >
        {current ? (
          <>
            <span className={cn("size-2 flex-none rounded-full", colourDotClass(current.status.colour))} />
            <span className="text-[13px] font-medium text-u-text">{current.status.label}</span>
            <span className="truncate text-[11.5px] text-u-text3">· {current.stage.label}</span>
          </>
        ) : (
          <span className="text-[12.5px] text-u-text3">{catalog ? "Choose a status" : "Loading your pipeline…"}</span>
        )}
        <Icon d={ICONS.chevronDown} size={14} className="ms-auto flex-none text-u-text3" />
      </button>
      {isOpen && catalog && (
        <div className="absolute inset-x-0 top-[calc(100%+4px)] z-30 overflow-hidden rounded-[10px] border border-u-border-strong bg-u-surface shadow-u-e3">
          <div className="flex items-center gap-2 border-b border-u-border px-3 py-2">
            <Icon d={ICONS.search} size={13} className="text-u-text3" />
            <input
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Find a status"
              aria-label="Find a status"
              className="min-w-0 flex-1 bg-transparent text-[12.5px] text-u-text outline-none placeholder:text-u-text3"
            />
          </div>
          <div role="listbox" aria-label="Status" className="max-h-[286px] overflow-y-auto px-1.5 pb-1.5 pt-1">
            {groups.map(({ stage, statuses }) => (
              <div key={stage.id} role="group" aria-label={stage.label}>
                <div className="flex items-center gap-2 px-2 pb-1 pt-2">
                  <span className={cn("size-[7px] rounded-full", colourDotClass(stage.colour))} />
                  <span className="font-mono text-[9.5px] font-semibold uppercase tracking-[0.11em] text-u-text3">{stage.label}</span>
                </div>
                {statuses.map((status) => {
                  const isSelected = status.key === value;
                  return (
                    <button
                      key={status.id}
                      type="button"
                      role="option"
                      aria-selected={isSelected}
                      onClick={() => {
                        onChange(status.key);
                        setIsOpen(false);
                        setQuery("");
                      }}
                      className={cn(
                        "flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start",
                        isSelected ? "bg-u-accent-tint" : "hover:bg-u-raised",
                      )}
                    >
                      <span className={cn("size-2 flex-none rounded-full", colourDotClass(status.colour))} />
                      <span className="flex-1 text-[12.5px] text-u-text">{status.label}</span>
                      {tagsOutOfProcess(stage, status) && (
                        <span className="rounded-[4px] bg-u-raised px-1.5 py-px font-mono text-[9px] font-semibold uppercase tracking-[0.05em] text-u-text3">
                          Out of process
                        </span>
                      )}
                      {status.key === startingKey && !isSelected && (
                        <span className="text-[10.5px] text-u-text3">Starting status</span>
                      )}
                      {isSelected && <Icon d={ICONS.check} size={13} className="text-u-accent" />}
                    </button>
                  );
                })}
              </div>
            ))}
            {groups.length === 0 && <p className="px-2.5 py-3 text-[12px] text-u-text3">No status matches “{query}”.</p>}
          </div>
        </div>
      )}
    </div>
  );
}
