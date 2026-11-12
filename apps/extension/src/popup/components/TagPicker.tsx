import { useRef, useState } from "react";
import type { CandidateTag } from "../../api/types";
import { colourPillClass } from "../../domain/pipelineStatus";
import { cn } from "../lib/cn";
import { ICONS } from "../lib/icons";
import { useDismiss } from "../lib/useDismiss";
import { Icon } from "./Icon";

/**
 * The person's tags as chips, and an Add tag popover over the workspace's catalog: tick a live tag, or type a name
 * nobody holds and create it. A retired tag a person still holds stays on them but is never offered.
 */
export function TagPicker({
  catalog,
  selectedIds,
  onToggle,
  onCreate,
  isCreating = false,
  disabled = false,
}: {
  catalog: CandidateTag[];
  selectedIds: string[];
  onToggle: (tagId: string, on: boolean) => void;
  onCreate: (label: string) => void;
  isCreating?: boolean;
  disabled?: boolean;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const [query, setQuery] = useState("");
  const containerRef = useRef<HTMLDivElement>(null);
  useDismiss(containerRef, isOpen, () => setIsOpen(false));

  const held = selectedIds
    .map((id) => catalog.find((tag) => tag.id === id))
    .filter((tag): tag is CandidateTag => Boolean(tag));
  const typed = query.trim();
  const offered = catalog.filter((tag) => !tag.retired && tag.label.toLowerCase().includes(typed.toLowerCase()));
  const canCreate = typed.length > 0 && !catalog.some((tag) => tag.label.toLowerCase() === typed.toLowerCase());

  return (
    <div ref={containerRef} className="relative">
      <div className="flex flex-wrap items-center gap-1.5">
        {held.map((tag) => (
          <span key={tag.id} className={cn("inline-flex items-center gap-1 rounded-full py-0.5 ps-2.5 pe-1.5 text-[11.5px] font-medium", colourPillClass(tag.colour))}>
            {tag.label}
            <button
              type="button"
              aria-label={`Remove ${tag.label}`}
              disabled={disabled}
              onClick={() => onToggle(tag.id, false)}
              className="grid size-3.5 place-items-center rounded-full opacity-70 hover:opacity-100"
            >
              <Icon d={ICONS.x} size={10} />
            </button>
          </span>
        ))}
        <button
          type="button"
          onClick={() => setIsOpen((open) => !open)}
          disabled={disabled}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          className="inline-flex items-center gap-1 rounded-full border border-dashed border-u-border-strong px-2.5 py-0.5 text-[11.5px] font-medium text-u-text3 transition hover:border-u-text3 hover:text-u-text disabled:opacity-60"
        >
          <Icon d={ICONS.plus} size={11} />
          Add tag
        </button>
      </div>
      {isOpen && (
        <div className="absolute inset-x-0 top-[calc(100%+4px)] z-30 overflow-hidden rounded-[10px] border border-u-border-strong bg-u-surface shadow-u-e3">
          <div className="flex items-center gap-2 border-b border-u-border px-3 py-2">
            <Icon d={ICONS.search} size={13} className="text-u-text3" />
            <input
              autoFocus
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter" && canCreate && !isCreating) {
                  event.preventDefault();
                  onCreate(typed);
                  setQuery("");
                }
              }}
              placeholder="Find or create a tag"
              aria-label="Find or create a tag"
              className="min-w-0 flex-1 bg-transparent text-[12.5px] text-u-text outline-none placeholder:text-u-text3"
            />
          </div>
          <div role="listbox" aria-multiselectable="true" aria-label="Tags" className="max-h-[220px] overflow-y-auto p-1.5">
            {offered.map((tag) => {
              const isOn = selectedIds.includes(tag.id);
              return (
                <button
                  key={tag.id}
                  type="button"
                  role="option"
                  aria-selected={isOn}
                  onClick={() => onToggle(tag.id, !isOn)}
                  className="flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start hover:bg-u-raised"
                >
                  <span
                    className={cn(
                      "grid size-3.5 flex-none place-items-center rounded-[4px] text-white",
                      isOn ? "bg-u-accent-solid" : "border border-u-border-strong",
                    )}
                  >
                    {isOn && <Icon d={ICONS.check} size={10} />}
                  </span>
                  <span className={cn("rounded-full px-2 py-px text-[11.5px] font-medium", colourPillClass(tag.colour))}>{tag.label}</span>
                  <span className="ms-auto font-mono text-[10.5px] text-u-text3">{tag.holders}</span>
                </button>
              );
            })}
            {canCreate && (
              <button
                type="button"
                disabled={isCreating}
                onClick={() => {
                  onCreate(typed);
                  setQuery("");
                }}
                className="mt-1 flex w-full items-center gap-2 rounded-u-control border-t border-u-border px-2 pb-1.5 pt-2 text-start hover:bg-u-raised"
              >
                <Icon d={ICONS.plus} size={13} className="text-u-accent" />
                <span className="text-[12.5px] text-u-text">Create tag “{typed}”</span>
              </button>
            )}
            {offered.length === 0 && !canCreate && <p className="px-2.5 py-2 text-[12px] text-u-text3">No tags yet.</p>}
          </div>
          <p className="border-t border-u-border px-3 py-[7px] text-[10.5px] leading-[1.5] text-u-text3">
            Tags are your workspace's. An admin renames or retires them in Settings → Candidate tags.
          </p>
        </div>
      )}
    </div>
  );
}
