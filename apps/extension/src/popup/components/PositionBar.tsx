import { useMemo, useRef, useState } from "react";
import { workspaceOrigin } from "../../workspaceOrigin";
import { cn } from "../lib/cn";
import { positionGroups } from "../lib/positionGroups";
import { ICONS } from "../lib/icons";
import { useDismiss } from "../lib/useDismiss";
import type { ProjectSelection } from "../hooks/useProjectSelection";
import { Icon } from "./Icon";
import { LogoTile } from "./LogoTile";

/**
 * The position the panel files into, chosen first and kept until changed — every field below is filed there. Opens a
 * switcher: a search over titles and clients, the recent positions first, then every live one grouped by client.
 */
export function PositionBar({ projects, workspaceName }: { projects: ProjectSelection; workspaceName: string | null }) {
  const [isOpen, setIsOpen] = useState(false);
  const containerRef = useRef<HTMLDivElement>(null);
  useDismiss(containerRef, isOpen, () => setIsOpen(false));
  const selected = projects.selectedProject;

  return (
    <div ref={containerRef} className="relative flex-none border-b border-u-border px-3.5 pb-[11px] pt-2.5">
      <div className="mb-1.5 flex items-center justify-between">
        <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-u-text3">Filing to</span>
        {workspaceName && <span className="truncate ps-3 font-mono text-[10.5px] text-u-text3">{workspaceName}</span>}
      </div>
      {projects.isLoading ? (
        <div aria-busy="true" className="h-[50px] animate-pulse rounded-[8px] bg-u-raised" />
      ) : selected ? (
        <button
          type="button"
          onClick={() => setIsOpen((open) => !open)}
          aria-haspopup="listbox"
          aria-expanded={isOpen}
          aria-label={`Filing to ${selected.positionTitle}. Change position`}
          className={cn(
            "flex w-full items-center gap-2.5 rounded-[8px] border bg-u-raised px-2.5 py-[7px] text-start transition hover:border-u-text3",
            isOpen ? "border-u-accent" : "border-u-border-strong",
          )}
        >
          <LogoTile name={selected.clientName ?? selected.positionTitle} src={selected.clientLogoUrl} />
          <span className="min-w-0 flex-1">
            <span className="block truncate text-[13px] font-semibold leading-[1.35] text-u-text">{selected.positionTitle}</span>
            {selected.clientName && (
              <span className="block truncate text-[11.5px] leading-[1.35] text-u-text3">{selected.clientName}</span>
            )}
          </span>
          <Icon d={ICONS.chevronsUpDown} size={15} className="flex-none text-u-text3" />
        </button>
      ) : (
        <NoPosition />
      )}
      {isOpen && selected && (
        <PositionSwitcher
          projects={projects}
          onPick={(projectId) => {
            projects.selectProject(projectId);
            setIsOpen(false);
          }}
        />
      )}
    </div>
  );
}

function NoPosition() {
  return (
    <div className="rounded-[8px] border border-dashed border-u-border-strong px-3 py-2.5">
      <p className="text-[12.5px] font-semibold text-u-text">No position to file into</p>
      <p className="mt-0.5 text-[11.5px] leading-[1.5] text-u-text3">
        You are not on any live position. Start one in Uncava, or ask a position's lead to add you to its team.
      </p>
      <button
        type="button"
        onClick={() => void chrome.tabs.create({ url: `${workspaceOrigin}/` })}
        className="mt-2 inline-flex items-center gap-1.5 text-[12px] font-medium text-u-accent hover:underline"
      >
        Open Uncava
        <Icon d={ICONS.externalLink} size={12} />
      </button>
    </div>
  );
}

function PositionSwitcher({ projects, onPick }: { projects: ProjectSelection; onPick: (projectId: string) => void }) {
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const groups = useMemo(() => positionGroups(projects.projects, projects.recentProjectIds, query), [projects, query]);
  const flat = groups.flatMap((group) => group.positions);

  const handleKey = (event: React.KeyboardEvent<HTMLInputElement>) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      const step = event.key === "ArrowDown" ? 1 : -1;
      setActive((index) => (index + step + flat.length) % Math.max(flat.length, 1));
    } else if (event.key === "Enter" && flat[active]) {
      event.preventDefault();
      onPick(flat[active].id);
    }
  };

  let index = -1;
  return (
    <div
      role="dialog"
      aria-label="Choose a position"
      className="absolute inset-x-2.5 top-[calc(100%-4px)] z-40 overflow-hidden rounded-[10px] border border-u-border-strong bg-u-surface shadow-u-e3"
    >
      <div className="flex items-center gap-2 border-b border-u-border px-3 py-2">
        <Icon d={ICONS.search} size={13} className="text-u-text3" />
        <input
          autoFocus
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setActive(0);
          }}
          onKeyDown={handleKey}
          placeholder="Search positions or clients"
          aria-label="Search positions or clients"
          className="min-w-0 flex-1 bg-transparent text-[12.5px] text-u-text outline-none placeholder:text-u-text3"
        />
      </div>
      <div role="listbox" aria-label="Positions" className="max-h-[340px] overflow-y-auto px-1.5 pb-1.5 pt-1">
        {groups.map((group) => (
          <div key={group.label} role="group" aria-label={group.label}>
            <div className="px-2 pb-1 pt-2 font-mono text-[9.5px] font-semibold uppercase tracking-[0.11em] text-u-text3">
              {group.label}
            </div>
            {group.positions.map((position) => {
              index += 1;
              const isSelected = position.id === projects.selectedProjectId;
              const isActive = index === active;
              return (
                <button
                  key={`${group.label}-${position.id}`}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  onClick={() => onPick(position.id)}
                  className={cn(
                    "flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-start",
                    isSelected ? "bg-u-accent-tint" : isActive ? "bg-u-raised" : "hover:bg-u-raised",
                  )}
                >
                  <LogoTile name={position.clientName ?? position.positionTitle} src={position.clientLogoUrl} size="sm" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-[12.5px] font-medium text-u-text">{position.positionTitle}</span>
                    {position.clientName && <span className="block truncate text-[11px] text-u-text3">{position.clientName}</span>}
                  </span>
                  {isSelected && <Icon d={ICONS.check} size={14} className="flex-none text-u-accent" />}
                </button>
              );
            })}
          </div>
        ))}
        {flat.length === 0 && <p className="px-2.5 py-3.5 text-[12px] text-u-text3">No position matches “{query}”.</p>}
      </div>
      <p className="border-t border-u-border px-3 py-[7px] text-[10.5px] leading-[1.5] text-u-text3">
        The positions you work on. Archived ones are left out.
      </p>
    </div>
  );
}
