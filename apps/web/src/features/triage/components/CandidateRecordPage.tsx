import { useEffect, useRef, useState, type ReactNode, type Ref } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { cn } from "../../../lib/cn";
import { EscapeLayerContext, useEscapeKey } from "../../../lib/useEscapeKey";
import { RecordBarLeadContext } from "../../candidates/components/RecordCornerControls";
import type { Candidate } from "../../candidates/api/types";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { pipelineSwatchClass, statusStyle } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import { executivesOf } from "../lib/triageVocabulary";

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
  const [listHidden, setListHidden] = useState(readListHidden);
  const hideList = (hidden: boolean) => {
    setListHidden(hidden);
    try {
      localStorage.setItem(LIST_HIDDEN_KEY, hidden ? "1" : "0");
    } catch {
      // A blocked store only forgets the choice.
    }
  };
  // Under 1024px the list floats over the record; wider, it takes its column back.
  const showList = () => {
    if (listHidden) hideList(false);
    if (!window.matchMedia?.("(min-width: 64rem)").matches) setListOpen(true);
  };
  const searchBox = useRef<HTMLInputElement>(null);
  const openRow = useRef<HTMLButtonElement>(null);
  // Topmost only, so a window the record opens over the page takes Esc first; then the list's overlay, then the page.
  const isTopLayer = useEscapeKey(true, () => (listOpen ? setListOpen(false) : onExit()));
  const needle = search.trim().toLowerCase();
  const shown = needle
    ? entries.filter(({ candidate }) =>
        [candidate.fullName, candidate.title, candidate.companyName].some((field) => field?.toLowerCase().includes(needle)),
      )
    : entries;
  const { catalog } = usePipelineCatalog();
  useEffect(() => {
    openRow.current?.scrollIntoView?.({ block: "nearest" });
  }, [openId]);
  // `/` searches the list, as in a mail client; never while typing, and never under a window over the page.
  useEffect(() => {
    const handleKey = (event: KeyboardEvent) => {
      if (event.key !== "/" || event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey) return;
      const target = event.target;
      if (target instanceof HTMLElement && (target.isContentEditable || target.closest("input, textarea, select")))
        return;
      if (!isTopLayer()) return;
      event.preventDefault();
      showList();
      requestAnimationFrame(() => searchBox.current?.focus());
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  });
  const listButton = (
    <button
      type="button"
      aria-expanded={listHidden ? false : listOpen}
      onClick={() => (listHidden ? showList() : setListOpen((open) => !open))}
      className={cn(
        "me-1.5 flex items-center gap-1.5 rounded-md border border-u-border px-2.5 py-1 text-note font-medium text-u-text2 hover:text-u-text",
        !listHidden && "lg:hidden",
      )}
    >
      <Icon d={ICONS.menu} size={14} />
      List
    </button>
  );

  return (
    // overflow-hidden: nothing positioned in a pane may reach past the page, or the whole page scrolls with it.
    <div className="relative flex min-h-0 min-w-0 flex-1 overflow-hidden">
      <nav
        aria-label={`${stageLabel} executives`}
        className={cn(
          "flex w-[280px] flex-none flex-col border-e border-u-border bg-u-surface",
          listOpen ? "absolute inset-y-0 start-0 z-20 shadow-u-e3" : "hidden",
          !listHidden && "lg:static lg:flex lg:shadow-none",
        )}
      >
        <div className="flex-none border-b border-u-border px-3 pb-3 pt-3">
          <div className="flex items-center gap-1.5">
            <button
              type="button"
              onClick={onExit}
              aria-label={`Back to ${stageLabel}`}
              className="flex items-center gap-1.5 rounded-md px-1 py-0.5 text-note font-medium text-u-text2 hover:text-u-text"
            >
              <Icon d={ICONS.back} size={14} />
              {stageLabel}
            </button>
            {!listHidden && (
              <button
                type="button"
                aria-label="Hide list"
                title="Hide list"
                onClick={() => {
                  setListOpen(false);
                  hideList(true);
                }}
                className="ms-auto hidden rounded-md p-1.5 text-u-text3 hover:bg-u-raised hover:text-u-text lg:flex"
              >
                <Icon d={ICONS.collapse} size={14} />
              </button>
            )}
          </div>
          <p className="mt-2 px-1 text-meta text-u-text3">
            {executivesOf(entries.length)} · same filters and sort as the grid
          </p>
          <label className="mt-2 flex items-center gap-2 rounded-md border border-u-border bg-u-canvas px-2.5 py-1.5">
            <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
            <input
              ref={searchBox}
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
              aria-keyshortcuts="/"
              className="min-w-0 flex-1 bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
            />
            <kbd
              aria-hidden="true"
              className="flex-none rounded border border-u-border px-1.5 font-mono text-meta text-u-text3"
            >
              /
            </kbd>
          </label>
        </div>
        <ul className="relative min-h-0 flex-1 overflow-y-auto py-1">
          {shown.map((entry) => (
            <li key={entry.candidate.id}>
              <RecordListRow
                projectId={projectId}
                catalog={catalog}
                entry={entry}
                isOpen={entry.candidate.id === openId}
                rowRef={entry.candidate.id === openId ? openRow : undefined}
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

      {/* The record's bar, header, body and footer down the first column; from 1280px its Details rail beside them. */}
      <section
        aria-label="Executive record"
        className="grid min-h-0 min-w-0 flex-1 grid-rows-[auto_auto_minmax(0,1fr)_auto] bg-u-surface xl:grid-cols-[minmax(0,1fr)_20rem]"
      >
        <EscapeLayerContext.Provider value={isTopLayer}>
          <RecordBarLeadContext.Provider value={listButton}>{children}</RecordBarLeadContext.Provider>
        </EscapeLayerContext.Provider>
      </section>
    </div>
  );
}

function RecordListRow({
  projectId,
  catalog,
  entry,
  isOpen,
  rowRef,
  onOpen,
}: {
  projectId: string;
  catalog: PipelineCatalog | null;
  entry: RecordListEntry;
  isOpen: boolean;
  rowRef?: Ref<HTMLButtonElement>;
  onOpen: () => void;
}) {
  const { candidate, companyLogo } = entry;
  const status = statusStyle(candidate.status, candidate, catalog);
  const subline = [candidate.title, candidate.companyName].filter(Boolean).join(" · ");
  return (
    <button
      ref={rowRef}
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

const LIST_HIDDEN_KEY = "uncava.recordListHidden";

function readListHidden(): boolean {
  try {
    return localStorage.getItem(LIST_HIDDEN_KEY) === "1";
  } catch {
    return false;
  }
}
