import { useInfiniteQuery, useIsMutating } from "@tanstack/react-query";
import type { SortingState } from "@tanstack/react-table";
import { useEffect, useRef, useState } from "react";
import { Link, useOutletContext } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { EmptyState, Skeleton } from "../../../components/ui";
import * as strategyApi from "../../strategy/api/strategyApi";
import * as sourcingApi from "../api/sourcingApi";
import type { SourcingSort, SourcingSortField } from "../api/types";
import { ColumnPicker } from "../components/ColumnPicker";
import { CompanyTable } from "../components/CompanyTable";
import { useColumnVisibility } from "../lib/useColumnVisibility";
import { useSourcingTable } from "../lib/useSourcingTable";

const PAGE_SIZE = 25;

/** How long a pause in typing must last before the filter refetches — as the company picker. */
const DEBOUNCE_MS = 250;

/** Mirror of the backend's SourcingService.MAX_QUERY_LENGTH. */
const MAX_QUERY_LENGTH = 100;

/**
 * The companies matching this project's saved Strategy scope (Project.dc.html, Sourcing screen): one
 * table, sorted and filtered by the server because the list is paged by the server. Which columns it
 * shows is the reader's choice, kept per project.
 */
export function SourcingPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const [draft, setDraft] = useState("");
  const [query, setQuery] = useState("");
  const [sorting, setSorting] = useState<SortingState>([]);
  const { visibility, setVisibility, reset: resetColumns } = useColumnVisibility(project.id);
  const sentinelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const timer = setTimeout(() => setQuery(draft.trim()), DEBOUNCE_MS);
    return () => clearTimeout(timer);
  }, [draft]);

  // The table's own sorting state is the source of truth; the API parameter is derived from it, which
  // is why a column's id is the backend's sort token rather than a display name.
  const sort: SourcingSort | null = sorting[0]
    ? { field: sorting[0].id as SourcingSortField, direction: sorting[0].desc ? "desc" : "asc" }
    : null;

  // A Strategy scope save is in flight (invalidation lands only once it commits). Hold this list until
  // then: keep the query disabled so it can't read a not-yet-written scope, and show the loader rather
  // than the pre-edit list. Without this, arriving here mid-save shows the stale companies for seconds.
  const isStrategySaving = useIsMutating({ mutationKey: strategyApi.STRATEGY_WRITE_KEY(project.id) }) > 0;

  const { data, fetchNextPage, hasNextPage, isFetching, isFetchingNextPage } = useInfiniteQuery({
    queryKey: sourcingApi.SOURCING_KEY(project.id, PAGE_SIZE, query, sort),
    queryFn: ({ pageParam, signal }) =>
      sourcingApi.getSourcingCompanies(project.id, pageParam, PAGE_SIZE, query, sort, signal),
    initialPageParam: 0,
    enabled: !isStrategySaving,
    getNextPageParam: (lastPage) => {
      const nextPage = lastPage.page + 1;
      return nextPage * lastPage.size < lastPage.totalCount ? nextPage : undefined;
    },
  });

  // Show the loader while a Strategy save is settling and for a base fetch (initial load, a criteria
  // change, a new filter or sort) alike — but not for infinite-scroll paging, which keeps the
  // accumulated list on screen. Without this, the pre-edit list renders during the save-and-refetch
  // window and the old companies flicker to the new ones in front of the user. Nothing unmounts while
  // it is true: the toolbar would take the filter box's focus with it on every debounced keystroke,
  // and the header row would pull the sort button out from under the click that set it going.
  const isReloading = isStrategySaving || (isFetching && !isFetchingNextPage);

  useEffect(() => {
    const sentinel = sentinelRef.current;
    // Not while reloading: the six skeleton rows leave the sentinel in view, and `fetchNextPage`
    // ignores `enabled`, so it would page the pre-edit scope on a loop — each page landing as a
    // success that clears the invalidation this reload is waiting on.
    if (!sentinel || isReloading) {
      return;
    }
    // Rooted at the sentinel's own scrollport: a wide table scrolls inside its wrapper and the page
    // behind it never moves, so a document-rooted observer would fire once and never again.
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries[0]?.isIntersecting && hasNextPage && !isFetchingNextPage) {
          fetchNextPage();
        }
      },
      { root: scrollParentOf(sentinel) },
    );
    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [fetchNextPage, hasNextPage, isFetchingNextPage, isReloading]);

  const pages = data?.pages ?? [];
  const totalCount = pages[0]?.totalCount ?? 0;
  const companies = pages.flatMap((page) => page.companies);
  const isEmpty = !isReloading && totalCount === 0;

  const table = useSourcingTable({
    companies,
    isReloading,
    sorting,
    onSortingChange: setSorting,
    visibility,
    onVisibilityChange: setVisibility,
  });

  return (
    <div className="flex min-h-0 flex-1 animate-fade-up flex-col">
      <div className="mb-4 flex-none">
        <div className="font-sans text-[19px] font-semibold leading-[1.2]">Sourcing</div>
        <p className="mt-1 max-w-[640px] text-[13px] text-text2">
          Companies matching Strategy's live criteria — the single source of truth. Criteria changes
          apply to these results going forward.
        </p>
      </div>

      {/* One controls row: the count on the left, everything that acts on the list on the right. It
          renders on the empty states too, so a filter that matches nothing can still be cleared from
          the box that caused it. */}
      <div className="mb-3 flex flex-none flex-wrap items-center justify-between gap-3">
        {isReloading ? (
          // Holds its line whether or not a count is known yet — collapsing it shifts the table up on
          // every sort and filter.
          <Skeleton className="h-[18px] w-[260px]" />
        ) : (
          <div className="font-sans text-[13px] text-text">
            <b className="text-amber">{totalCount.toLocaleString("en-US")}</b>{" "}
            {query
              ? `companies named "${query}" match the current criteria`
              : "companies match the current criteria"}
          </div>
        )}

        <div className="flex flex-none items-center gap-2">
          <div className="relative">
            <Icon
              d={ICONS.search}
              size={13}
              className="pointer-events-none absolute left-[10px] top-1/2 -translate-y-1/2 text-text3"
            />
            <input
              type="text"
              value={draft}
              onChange={(event) => setDraft(event.target.value)}
              // Mirrors the server's MAX_QUERY_LENGTH. Without it an over-long paste 400s, and the
              // failure renders as a plain "nothing matched" — a validation error told as a fact.
              maxLength={MAX_QUERY_LENGTH}
              placeholder="Filter by company name"
              aria-label="Filter by company name"
              className="w-[210px] rounded-[8px] border border-line bg-panel py-[6px] pl-[29px] pr-[26px] font-sans text-[12.5px] text-text placeholder:text-text3 focus:border-text3 focus:outline-none"
            />
            {draft && (
              <button
                type="button"
                aria-label="Clear name filter"
                onClick={() => setDraft("")}
                className="absolute right-[7px] top-1/2 flex -translate-y-1/2 text-text3 hover:text-text"
              >
                <Icon d="M18 6 6 18M6 6l12 12" size={13} />
              </button>
            )}
          </div>
          <Link
            to={`/projects/${project.id}/strategy`}
            className="flex items-center gap-[6px] rounded-[8px] border border-line px-3 py-[6px] font-sans text-[12.5px] font-medium text-text2 hover:border-text3 hover:text-text"
          >
            <Icon d={ICONS.strategy} size={14} />
            Edit criteria in Strategy
          </Link>
          <ColumnPicker columns={table.getAllLeafColumns()} onReset={resetColumns} />
        </div>
      </div>

      {isEmpty && query ? (
        // A filter matching nothing is a different dead end from an unscoped mandate: the criteria are
        // fine, the name isn't. Sending someone to Strategy to fix a typo would be the wrong door.
        <EmptyState
          icon={<Icon d={ICONS.sourcing} size={22} />}
          title={`No companies match "${query}"`}
          body="No company in this mandate's criteria carries that name. Try a shorter fragment."
        >
          <button
            type="button"
            onClick={() => setDraft("")}
            className="rounded-[8px] border border-line px-[13px] py-[7px] font-sans text-[13px] font-medium text-text2 hover:border-text3 hover:text-text"
          >
            Clear name filter
          </button>
        </EmptyState>
      ) : isEmpty ? (
        <EmptyState
          icon={<Icon d={ICONS.sourcing} size={22} />}
          title="No companies match yet"
          body="Set a Sector or Company Size filter in Strategy to start sourcing companies for this mandate."
        >
          <Link
            to={`/projects/${project.id}/strategy`}
            className="rounded-[8px] border border-amber-btn bg-amber-btn px-[13px] py-[7px] font-sans text-[13px] font-semibold text-[#141414] hover:brightness-105"
          >
            Go to Strategy
          </Link>
        </EmptyState>
      ) : (
        <CompanyTable
          table={table}
          isReloading={isReloading}
          isFetchingNextPage={isFetchingNextPage}
          sentinelRef={sentinelRef}
        />
      )}
    </div>
  );
}

/** The nearest ancestor that actually scrolls, or `null` for the document itself. */
function scrollParentOf(element: HTMLElement): HTMLElement | null {
  let candidate = element.parentElement;
  while (candidate) {
    const overflowY = getComputedStyle(candidate).overflowY;
    if (overflowY === "auto" || overflowY === "scroll") {
      return candidate;
    }
    candidate = candidate.parentElement;
  }
  return null;
}
