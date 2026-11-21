import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { cn } from "../../../lib/cn";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import type { Candidate } from "../../candidates/api/types";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { pipelineSwatchClass, statusStyle } from "../../candidates/lib/pipelineStatus";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";

/** One executive in the full page's list, with the logo of the company the grid's row drew them at. */
export interface RecordListEntry {
  candidate: Candidate;
  companyLogo: string | null;
}

/** The executive record full page (`Position.dc.html`, `recordMode=page`): the grid's executives beside the record. */
export function CandidateRecordPage({
  projectId,
  stageLabel,
  entries,
  openId,
  onOpen,
  onExit,
  children,
}: {
  projectId: string;
  stageLabel: string;
  entries: readonly RecordListEntry[];
  openId: string;
  onOpen: (candidate: Candidate) => void;
  onExit: () => void;
  /** The record itself: the peek's header, tabs, body and foot. */
  children: ReactNode;
}) {
  const [search, setSearch] = useState("");
  const [listOpen, setListOpen] = useState(false);
  // Topmost only, so a window the record opens over the page takes Esc first; then the list's overlay, then the page.
  useEscapeKey(true, () => (listOpen ? setListOpen(false) : onExit()));
  const needle = search.trim().toLowerCase();
  const shown = needle
    ? entries.filter(({ candidate }) =>
        [candidate.fullName, candidate.title, candidate.companyName].some((field) => field?.toLowerCase().includes(needle)),
      )
    : entries;
  const count = `${entries.length} ${entries.length === 1 ? "executive" : "executives"}`;

  return (
    <div className="relative flex min-h-0 min-w-0 flex-1">
      <nav
        aria-label={`${stageLabel} executives`}
        className={cn(
          "flex w-[290px] flex-none flex-col border-e border-u-border bg-u-surface",
          listOpen ? "absolute inset-y-0 start-0 z-20 shadow-u-e3" : "hidden",
          "lg:static lg:flex lg:shadow-none",
        )}
      >
        <div className="flex-none border-b border-u-border px-3 pb-3 pt-3">
          <button
            type="button"
            onClick={onExit}
            aria-label={`Back to ${stageLabel}`}
            className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-note font-medium text-u-text2 hover:text-u-text"
          >
            <Icon d={ICONS.back} size={14} />
            {stageLabel}
          </button>
          <p className="mt-2 px-1 text-meta text-u-text3">
            {count} · same filters and sort as the grid
          </p>
          <label className="mt-2 flex items-center gap-2 rounded-md border border-u-border bg-u-canvas px-2.5 py-1.5">
            <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
            <input
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              // Esc clears the box, or leaves it once empty, and goes no further: the page's own Esc is leaving it.
              onKeyDown={(event) => {
                if (event.key !== "Escape") return;
                event.stopPropagation();
                if (search) setSearch("");
                else event.currentTarget.blur();
              }}
              placeholder="Search executives"
              aria-label="Search executives"
              className="min-w-0 flex-1 bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
            />
          </label>
        </div>
        <ul className="min-h-0 flex-1 overflow-y-auto py-1">
          {shown.map((entry) => (
            <li key={entry.candidate.id}>
              <RecordListRow
                projectId={projectId}
                entry={entry}
                isOpen={entry.candidate.id === openId}
                onOpen={() => {
                  setListOpen(false);
                  onOpen(entry.candidate);
                }}
              />
            </li>
          ))}
          {shown.length === 0 && (
            <li className="px-4 py-6 text-note text-u-text3">
              {needle ? "No executives match that search." : "No executives on this page."}
            </li>
          )}
        </ul>
      </nav>

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <div className="flex-none px-3 pt-2 lg:hidden">
          <button
            type="button"
            aria-expanded={listOpen}
            onClick={() => setListOpen((open) => !open)}
            className="flex items-center gap-1.5 rounded-md border border-u-border px-2.5 py-1 text-note font-medium text-u-text2 hover:text-u-text"
          >
            <Icon d={ICONS.menu} size={14} />
            List
          </button>
        </div>
        <section
          aria-label="Executive record"
          className="flex min-h-0 w-full max-w-[820px] flex-1 flex-col bg-u-surface lg:border-e lg:border-u-border"
        >
          {children}
        </section>
      </div>
    </div>
  );
}

function RecordListRow({
  projectId,
  entry,
  isOpen,
  onOpen,
}: {
  projectId: string;
  entry: RecordListEntry;
  isOpen: boolean;
  onOpen: () => void;
}) {
  const { catalog } = usePipelineCatalog();
  const { candidate, companyLogo } = entry;
  const status = statusStyle(candidate.status, candidate, catalog);
  const subline = [candidate.title, candidate.companyName].filter(Boolean).join(" · ");
  return (
    <button
      type="button"
      onClick={onOpen}
      aria-current={isOpen ? "true" : undefined}
      className={cn(
        "flex w-full items-center gap-2.5 border-s-2 px-3 py-2 text-start transition hover:bg-u-raised",
        isOpen ? "border-u-accent bg-u-raised" : "border-transparent",
      )}
    >
      <span className="relative flex-none">
        <CandidateAvatar projectId={projectId} candidate={candidate} className="size-9 text-meta" />
        <span
          title={status.label}
          aria-hidden="true"
          className={cn(
            "absolute -bottom-0.5 -end-0.5 size-2.5 rounded-full ring-2 ring-u-surface",
            pipelineSwatchClass(status.colour),
          )}
        />
      </span>
      <span className="min-w-0 flex-1">
        <span className="flex min-w-0 items-center gap-2">
          <span className="truncate text-note font-semibold text-u-text">{candidate.fullName}</span>
          {candidate.companyName && (
            <span className="ms-auto flex-none">
              <CompanyLogo name={candidate.companyName} logo={companyLogo} size={16} />
            </span>
          )}
        </span>
        {subline && <span className="block truncate text-meta text-u-text3">{subline}</span>}
        <span className="sr-only">{status.label}</span>
      </span>
    </button>
  );
}
