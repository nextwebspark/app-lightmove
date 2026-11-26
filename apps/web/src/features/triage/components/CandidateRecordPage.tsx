import { useEffect, useRef, useState, type ReactNode, type Ref } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { LIST_PANE_WIDTH } from "../../../components/layout/listPane";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { LinesSkeleton } from "../../../components/ui/Skeleton";
import { cn } from "../../../lib/cn";
import { EscapeLayerContext, useEscapeKey } from "../../../lib/useEscapeKey";
import type { Candidate } from "../../candidates/api/types";
import { CandidateAvatar } from "../../candidates/components/CandidateAvatar";
import { CORNER_BUTTON } from "../../candidates/components/RecordCornerControls";
import { pipelineSwatchClass, statusStyle } from "../../candidates/lib/pipelineStatus";
import type { PipelineCatalog } from "../../workspace/api/types";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";

/** One executive in the full page's list, with the logo of the company the grid's row drew them at. */
export interface RecordListEntry {
  candidate: Candidate;
  companyLogo: string | null;
}

/** The stage's Executives view: the grid's executives beside the record, one at a time. */
export function CandidateRecordPage({
  projectId,
  stageLabel,
  entries,
  openId,
  onOpen,
  onLeave,
  footer,
  children,
}: {
  projectId: string;
  stageLabel: string;
  entries: readonly RecordListEntry[];
  openId: string;
  onOpen: (candidate: Candidate) => void;
  /** Esc's way back to where the view was opened from, where it was opened from somewhere. */
  onLeave?: () => void;
  footer?: ReactNode;
  children: (listButton: ReactNode) => ReactNode;
}) {
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
  const showList = () => {
    if (listHidden) hideList(false);
    if (!window.matchMedia?.("(min-width: 64rem)").matches) setListOpen(true);
  };
  const openRow = useRef<HTMLButtonElement>(null);
  // Topmost only, so a window the record opens over the page takes Esc first; then the list's overlay, then leaving.
  const isTopLayer = useEscapeKey(true, () => (listOpen ? setListOpen(false) : onLeave?.()));
  const { catalog } = usePipelineCatalog();
  useEffect(() => {
    openRow.current?.scrollIntoView?.({ block: "nearest" });
  }, [openId]);
  const listButton = (
    <>
      {!listHidden && (
        <button
          type="button"
          aria-label="Hide list"
          title="Hide list"
          onClick={() => {
            setListOpen(false);
            hideList(true);
          }}
          className="me-1 hidden rounded-md p-1.5 text-u-text3 hover:bg-u-raised hover:text-u-text lg:flex"
        >
          <Icon d={ICONS.collapse} size={15} />
        </button>
      )}
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
    </>
  );

  return (
    // overflow-hidden: nothing positioned in a pane may reach past the page, or the whole page scrolls with it.
    <div className="relative flex min-h-0 min-w-0 flex-1 overflow-hidden">
      <nav
        aria-label={`${stageLabel} executives`}
        className={cn(
          "flex flex-none flex-col border-e border-u-border bg-u-surface",
          LIST_PANE_WIDTH,
          listOpen ? "absolute inset-y-0 start-0 z-20 shadow-u-e3" : "hidden",
          !listHidden && "lg:static lg:flex lg:shadow-none",
        )}
      >
        <ul className="relative min-h-0 flex-1 overflow-y-auto py-1">
          {entries.map((entry) => (
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
          {entries.length === 0 && <li className="px-4 py-6 text-note text-u-text3">No executives on this page.</li>}
        </ul>
        {footer}
      </nav>

      <section aria-label="Executive record" className="flex min-h-0 min-w-0 flex-1 flex-col bg-u-surface">
        <EscapeLayerContext.Provider value={isTopLayer}>{children(listButton)}</EscapeLayerContext.Provider>
      </section>
    </div>
  );
}

/** Why the Executives view has no one to open; the pager stays, since a later page may hold someone. */
export type RecordPageWaiting = "error" | "loading" | "filtered" | "pageEmpty" | "empty";

export function RecordPageEmpty({
  waiting,
  stageLabel,
  footer,
}: {
  waiting: RecordPageWaiting;
  stageLabel: string;
  footer?: ReactNode;
}) {
  return (
    <div className="flex min-h-0 min-w-0 flex-1 flex-col">
      <div className="flex min-h-0 flex-1 items-start justify-center p-10 text-note text-u-text3">
        {waiting === "loading" ? (
          <LinesSkeleton lines={6} className="w-full max-w-xl" />
        ) : (
          EMPTY_MESSAGES[waiting](stageLabel)
        )}
      </div>
      {footer}
    </div>
  );
}

const EMPTY_MESSAGES: Record<Exclude<RecordPageWaiting, "loading">, (stageLabel: string) => string> = {
  error: () => "This list could not be loaded.",
  filtered: () => "No executives match that search.",
  pageEmpty: () => "No executives on this page.",
  empty: (stageLabel) => `No executives on ${stageLabel} yet.`,
};

/** The Executives view's list runs a page of the grid at a time; this turns it. */
export function RecordListPager({
  page,
  lastPage,
  onPage,
}: {
  page: number;
  lastPage: number;
  onPage: (page: number) => void;
}) {
  return (
    <div className="flex flex-none items-center gap-1 border-t border-u-border px-3 py-2">
      <span className="font-u-num text-meta tabular-nums text-u-text3">
        Page {page + 1} of {lastPage + 1}
      </span>
      <button
        type="button"
        aria-label="Previous page"
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
        className={cn(CORNER_BUTTON, "ms-auto")}
      >
        <Icon d={ICONS.chevronDown} size={14} className="rotate-90" />
      </button>
      <button
        type="button"
        aria-label="Next page"
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
        className={CORNER_BUTTON}
      >
        <Icon d={ICONS.chevronDown} size={14} className="-rotate-90" />
      </button>
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
