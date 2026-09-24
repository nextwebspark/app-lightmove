import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { RowSelectionState } from "@tanstack/react-table";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { FullscreenButton } from "../../../components/ui";
import { useToast } from "../../../components/ui/Toast";
import { useAssistant } from "../../assistant/AssistantProvider";
import { useAuth } from "../../auth/AuthProvider";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { hasRoomForRails } from "../../../lib/viewport";
import { DEFAULT_PAGE_SIZE } from "../../../lib/paging";
import { useAutosave } from "../../../lib/useAutosave";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import * as triageApi from "../../triage/api/triageApi";
import type { TriageCompanyStatus } from "../../triage/api/types";
import { TRIAGE_STAGES, stageByStatus } from "../../triage/lib/triageStages";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type {
  CompanyResult,
  CompanySort,
  SearchVisibility,
  Strategy,
  StrategyFilter,
} from "../api/types";
import { CompanyResultsTable } from "../components/CompanyResultsTable";
import { MarketCompanyDrawer } from "../components/MarketCompanyDrawer";
import { DEFAULT_COLUMN_VISIBILITY, companyColumns } from "../lib/companyColumns";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridSort } from "../../../lib/useGridSort";
import { COMPANY_SORT_FIELDS } from "../lib/companyColumns";
import { FilterSidebar } from "../components/FilterSidebar";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { SelectionAction, SelectionActionBar } from "../../../components/ui/SelectionActionBar";
import { StrategyToolbar } from "../components/StrategyToolbar";

const COMPANY_LAYOUT_COLUMNS = layoutColumnsOf(companyColumns);

const DEFAULT_SORT: CompanySort = { field: "employees", direction: "desc" };

/** A stable empty selection, so "nothing ticked" is one identity rather than a new object per render. */
const NOTHING_SELECTED: RowSelectionState = {};

/** What the rail draws until the mandate's stored filter lands. Selects the whole universe. */
const NO_FILTER: StrategyFilter = {
  industries: [],
  keywords: [],
  marketSegments: [],
  countries: [],
  employeeBands: [],
  revenueBands: [],
  employeeRange: null,
  revenueRange: null,
};

export function StrategyPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  // Keyed on the project so switching mandates remounts with that mandate's filter rather than
  // carrying the last one's draft across.
  return <StrategyEditor key={project.id} />;
}

/**
 * The search screen: filter rail on the left, the universe it selects on the right.
 *
 * <p>The filter autosaves — there is no Save button, matching every other editing surface in the
 * product. Every write invalidates the reads that depend on the scope, and cancels them first: a read
 * left running would resolve after the invalidation and reinstate the pre-edit companies as fresh for
 * the whole staleTime.
 */
function StrategyEditor() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const queryClient = useQueryClient();
  const toast = useToast();
  const { openAssistant } = useAssistant();
  // Whose searches are "Mine" in the dropdown. The list already excludes other people's private ones,
  // so this only splits what arrived, never widens it.
  const { user } = useAuth();

  const strategy = useQuery({
    queryKey: strategyApi.STRATEGY_KEY(project.id),
    queryFn: () => strategyApi.getStrategy(project.id),
  });
  const facets = useQuery({
    queryKey: companiesApi.FACETS_KEY,
    queryFn: companiesApi.getFacets,
    // The counts are over the whole universe and change only when the pipeline loads, so this
    // survives every filter edit and every mandate switch.
    staleTime: 10 * 60 * 1000,
  });

  const [filter, setFilter] = useState<StrategyFilter>(() => strategy.data?.filter ?? NO_FILTER);
  const [showFilters, setShowFilters] = useState(hasRoomForRails);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [sort, setSort] = useGridSort("strategy", project.id, COMPANY_SORT_FIELDS, DEFAULT_SORT);
  const [openCompany, setOpenCompany] = useState<CompanyResult | null>(null);
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "strategy",
    project.id,
    DEFAULT_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout("strategy", COMPANY_LAYOUT_COLUMNS);
  const [isFullscreen, toggleFullscreen] = useFullscreen();

  /*
   * The grid's own `rowSelectionFeature` state, held here rather than inside the table because the
   * bulk bar acts on it and outlives any one page of results. Keyed by `apolloAccountId` — the
   * table's `getRowId` — and the feature deletes a key rather than storing `false`, so the keys are
   * exactly what is ticked.
   */
  const [rowSelection, setRowSelection] = useState<RowSelectionState>(NOTHING_SELECTED);
  const selectedIds = useMemo(() => Object.keys(rowSelection), [rowSelection]);
  const clearSelection = useCallback(() => setRowSelection(NOTHING_SELECTED), []);

  // A keystroke should narrow the list, not fire a request per character.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  /*
   * The stored filter, adopted the once — never on every change to `strategy.data`. Between a chip
   * click and its autosave the draft on screen is ahead of the server, and re-adopting the response
   * to that write would put back what was clicked in the meantime. Nothing can edit the filter before
   * it lands: the rail is the only surface that writes it, and the rail is what waits.
   */
  const hasAdoptedStoredFilter = useRef(strategy.data !== undefined);
  useEffect(() => {
    if (hasAdoptedStoredFilter.current || !strategy.data) return;
    hasAdoptedStoredFilter.current = true;
    setFilter(strategy.data.filter);
  }, [strategy.data]);

  /*
   * Any change to what is being asked returns to the first page and drops the selection: page 4 of a
   * filter that now matches two companies is an empty table over a non-empty result, and a tick made
   * under the last scope would act on companies this one no longer contains and the user can no
   * longer see. A tick does survive a page turn — picking twelve companies across three pages is the
   * case the bulk bar exists for — so this fires on the scope, never on the page.
   *
   * <p>Adopting the stored filter is deliberately not such a change, which is why this no longer
   * keys off `filter`: the server has been scoping the results by that filter all along, so its
   * arrival must not stomp a page turned, or a row ticked, while /strategy was still in flight.
   */
  const resetScope = useCallback(() => {
    setPage(0);
    clearSelection();
  }, [clearSelection]);

  useEffect(() => resetScope(), [debouncedQuery, sort, resetScope]);

  const refreshScopedReads = async () => {
    const scopedKeys = [
      strategyApi.STRATEGY_COMPANIES_KEY_PREFIX(project.id),
      triageApi.TRIAGE_KEY_PREFIX(project.id),
    ];
    await Promise.all(scopedKeys.map((queryKey) => queryClient.cancelQueries({ queryKey })));
    scopedKeys.forEach((queryKey) => void queryClient.invalidateQueries({ queryKey }));
  };

  const filterWrite = useMutation({
    mutationKey: strategyApi.STRATEGY_WRITE_KEY(project.id),
    // The side effects live inside mutationFn so they still run when useAutosave flushes on unmount.
    mutationFn: async (payload: StrategyFilter) => {
      try {
        queryClient.setQueryData(strategyApi.STRATEGY_KEY(project.id), await strategyApi.putFilter(project.id, payload));
        await refreshScopedReads();
      } catch (error) {
        toast(messageFor(error));
        throw error;
      }
    },
  });
  const autosave = useAutosave<StrategyFilter>((payload) => filterWrite.mutateAsync(payload));

  const applyFilter = (next: StrategyFilter) => {
    setFilter(next);
    autosave.schedule(next);
    resetScope();
  };

  const companies = useQuery({
    queryKey: strategyApi.STRATEGY_COMPANIES_KEY(project.id, page, pageSize, debouncedQuery, sort),
    queryFn: ({ signal }) =>
      strategyApi.getCompanies(project.id, page, pageSize, debouncedQuery, sort, signal),
    // Paging without blanking the table, which would make every page turn look like a reload.
    placeholderData: keepPreviousData,
  });

  const saveSearch = useMutation({
    // Flush first, for the same reason "Add all" does: the request carries only a name and the server
    // snapshots the *stored* filter, so a save inside the debounce window records the scope as it was
    // before the last chip click — silently, and for every later load of that search.
    mutationFn: async ({ name, visibility }: { name: string; visibility: SearchVisibility }) => {
      await autosave.flush();
      return strategyApi.saveSearch(project.id, name, visibility);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(project.id) });
      toast("Search saved");
    },
    onError: (error) => toast(messageFor(error)),
  });

  const editSearch = useMutation({
    mutationFn: ({
      searchId,
      ...patch
    }: {
      searchId: string;
      name?: string;
      visibility?: SearchVisibility;
    }) => strategyApi.patchSearch(project.id, searchId, patch),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(project.id) });
    },
    onError: (error) => toast(messageFor(error)),
  });

  const overwriteSearch = useMutation({
    // Flushed first for the same reason saveSearch is — see strategyApi.overwriteSearch.
    mutationFn: async (searchId: string) => {
      await autosave.flush();
      return strategyApi.overwriteSearch(project.id, searchId);
    },
    onSuccess: (search) => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(project.id) });
      toast(`${search.name} updated`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  const deleteSearch = useMutation({
    mutationFn: (searchId: string) => strategyApi.deleteSearch(project.id, searchId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(project.id) });
      toast("Search deleted");
    },
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * The one way the off-limits list is written, whoever asked. It takes what the list should become
   * from what it currently *is* — read from the cache here rather than trusted from a caller's render
   * — because the endpoint replaces the list wholesale, so an edit built on a stale copy silently
   * unbars everything that arrived after it.
   *
   * <p>It writes immediately rather than through the autosave timer: barring a company is a decision,
   * not a draft. The flush is still needed because the response is the whole Strategy and goes
   * straight into the cache, so a filter edit still sitting in the timer would be overwritten by the
   * copy the server had before it.
   */
  const writeOffLimits = async (next: (barred: string[]) => string[]) => {
    await autosave.flush();
    const stored = queryClient.getQueryData<Strategy>(strategyApi.STRATEGY_KEY(project.id));
    const barred = (stored?.offLimits ?? []).map((entry) => entry.apolloAccountId);
    const wanted = next(barred);
    if (wanted.length === barred.length && wanted.every((id) => barred.includes(id))) return;
    queryClient.setQueryData(
      strategyApi.STRATEGY_KEY(project.id),
      await strategyApi.putOffLimits(project.id, wanted),
    );
    await refreshScopedReads();
  };

  /*
   * Both writers share one `scope`, so the rail and the panel queue behind each other instead of
   * racing to replace the same list.
   */
  const OFF_LIMITS_SCOPE = { id: `off-limits-${project.id}` };

  /** The rail, which renders the whole list and hands back the whole list it wants. */
  const offLimitsWrite = useMutation({
    scope: OFF_LIMITS_SCOPE,
    mutationFn: (apolloAccountIds: string[]) => writeOffLimits(() => apolloAccountIds),
    onError: (error) => toast(messageFor(error)),
  });

  /** The panel, which knows only the one company it is barring. */
  const barCompany = useMutation({
    scope: OFF_LIMITS_SCOPE,
    mutationFn: async (company: CompanyResult) => {
      await writeOffLimits((barred) =>
        barred.includes(company.apolloAccountId) ? barred : [...barred, company.apolloAccountId],
      );
      return company;
    },
    onSuccess: (company) => toast(`${company.companyName} is off-limits for this mandate`),
    onError: (error) => toast(messageFor(error)),
  });

  const addOne = useMutation({
    mutationFn: ({ company, status }: { company: CompanyResult; status: TriageCompanyStatus }) =>
      triageApi.addMarketCompany(project.id, company.apolloAccountId, { status }),
    onSuccess: (added, { company, status }) => {
      // Not just the triage prefix: the search excludes a company the moment it is triaged, so the
      // grid behind this panel has to refetch too, or the row it was just read from lingers on screen
      // until something else happens to invalidate it.
      void refreshScopedReads();
      // The stage that comes back, never the one asked for: a company the mandate already holds is
      // returned untouched, so "Shortlisted" on a declined row files nothing. Saying it did would
      // leave a mandate believing in a shortlist entry that is not there. The search itself excludes
      // an already-triaged company, so this only fires from a race — the panel open on a row another
      // tab just triaged — not from the ordinary path.
      toast(
        added.status === status
          ? `${company.companyName} added to ${stageByStatus(status).label}`
          : `${company.companyName} is already in this mandate, at ${stageByStatus(added.status).label}`,
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  const addAll = useMutation({
    mutationFn: async () => {
      // Flush first: "Add all" acts on the *stored* filter, and a debounced edit still in the
      // timer would mean the server adds companies from the filter as it was two chips ago.
      await autosave.flush();
      return triageApi.addAllInScope(project.id);
    },
    onSuccess: (result) => {
      // Every company just taken in stops matching the search that found it.
      void refreshScopedReads();
      toast(
        `Added ${result.added} companies to universe${result.skipped > 0 ? `, ${result.skipped} already there` : ""}`,
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  /**
   * The selection bar's three buttons. One request rather than a POST per company: the toast states a
   * number, and a loop would leave it guessing after the fourth of forty failed.
   *
   * <p>No autosave flush, unlike "Add all": this carries the ids it is adding, so a filter edit still
   * sitting in the timer cannot change what it means.
   */
  const addSelected = useMutation({
    mutationFn: (status: TriageCompanyStatus) =>
      triageApi.addSelectedCompanies(project.id, selectedIds, status),
    onSuccess: (result, status) => {
      // Every company just moved stops matching the search that found it.
      void refreshScopedReads();
      clearSelection();
      // Every one skipped is a company the mandate already holds, and it keeps the stage it is at —
      // so saying so is the difference between "nothing happened" and "they were already there".
      toast(
        `${result.added} ${result.added === 1 ? "company" : "companies"} moved to ${stageByStatus(status).label}` +
          (result.skipped > 0 ? `, ${result.skipped} already in this mandate` : ""),
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  if (strategy.isError) {
    return (
      <div className="p-10 text-center font-mono text-[13px] text-u-text3">
        This mandate&rsquo;s search could not be loaded.
      </div>
    );
  }

  const data = strategy.data;

  return (
    /* No negative margins and no viewport arithmetic: the shell gives this tab the whole main area
       and a definite height (FULL_BLEED_TABS in ProjectLayout), so the height is inherited rather
       than guessed from a hard-coded 98px of chrome that any topbar change would falsify. */
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <StrategyToolbar
        filter={filter}
        filterPending={!data}
        searches={data?.searches ?? []}
        viewerId={user?.id ?? null}
        showFilters={showFilters}
        onToggleFilters={() => setShowFilters((shown) => !shown)}
        query={query}
        onQuery={setQuery}
        onSaveSearch={(name, visibility) => saveSearch.mutate({ name, visibility })}
        onLoadSearch={applyFilter}
        onRenameSearch={(searchId, name) => editSearch.mutate({ searchId, name })}
        onSetSearchVisibility={(searchId, visibility) => editSearch.mutate({ searchId, visibility })}
        onOverwriteSearch={(searchId) => overwriteSearch.mutate(searchId)}
        onDeleteSearch={(searchId) => deleteSearch.mutate(searchId)}
        onAddAll={() => addAll.mutate()}
        onAiResearch={() => openAssistant(project.id)}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
        savingSearch={saveSearch.isPending}
        addingAll={addAll.isPending}
      />

      <div className="flex min-h-0 flex-1">
        {showFilters &&
          (data ? (
            <>
              <div
                className="fixed inset-0 z-[90] bg-u-scrim lg:hidden"
                onClick={() => setShowFilters(false)}
              />
              <FilterSidebar
                facets={facets.data}
                facetsError={facets.isError}
                filter={filter}
                offLimits={data.offLimits}
                onChange={applyFilter}
                onOffLimitsChange={(ids) => offLimitsWrite.mutate(ids)}
                onClose={() => setShowFilters(false)}
              />
            </>
          ) : (
            <FilterRailPlaceholder />
          ))}

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-2">
          {/* The bar floats over the grid rather than over the viewport, so it centres on the table
              instead of drifting by half the width of the nav rail, and it never covers the paging
              row underneath. */}
          <div className="relative flex min-h-0 flex-1 flex-col">
            <CompanyResultsTable
              companies={companies.data?.companies ?? []}
              sort={sort}
              onSortChange={setSort}
              columnVisibility={columnVisibility}
              onColumnVisibilityChange={setColumnVisibility}
              layout={layout}
              onLayoutChange={setLayout}
              loading={companies.isFetching}
              error={companies.isError}
              rowSelection={rowSelection}
              onRowSelectionChange={setRowSelection}
              onOpenCompany={setOpenCompany}
            />
            {selectedIds.length > 0 && (
              <SelectionActionBar
                count={selectedIds.length}
                noun="company"
                plural="companies"
                onClear={clearSelection}
              >
                {TRIAGE_STAGES.map((stage) => (
                  <SelectionAction
                    key={stage.status}
                    icon={stage.icon}
                    label={stage.label}
                    tone={stage.status === "declined" ? "danger" : "neutral"}
                    disabled={addSelected.isPending}
                    onClick={() => addSelected.mutate(stage.status)}
                  />
                ))}
              </SelectionActionBar>
            )}
          </div>
          <PaginationBar
            page={page}
            size={pageSize}
            totalCount={companies.data?.totalCount}
            onPage={setPage}
            onSize={setPageSize}
            trailing={<FullscreenButton active={isFullscreen} onToggle={toggleFullscreen} />}
          />
        </div>
      </div>

      <MarketCompanyDrawer
        company={openCompany}
        onClose={() => setOpenCompany(null)}
        onTriage={(company, status) => {
          setOpenCompany(null);
          addOne.mutate({ company, status });
        }}
        onOffLimits={(company) => {
          setOpenCompany(null);
          barCompany.mutate(company);
        }}
        barring={barCompany.isPending}
      />
    </div>
  );
}

/**
 * The rail's footprint while the stored filter is still in flight. The grid no longer waits on
 * /strategy — the server scopes the results from the stored filter itself — but the rail does: drawn
 * over an empty filter it would read as a mandate that has selected nothing, and then fill with
 * chips. Nothing below `lg`, where the rail overlays the results rather than sitting beside them.
 */
function FilterRailPlaceholder() {
  return (
    <div
      aria-hidden="true"
      className="hidden animate-pulse border-e border-u-border bg-u-surface lg:block lg:w-[19%] lg:min-w-[264px] lg:max-w-[312px] lg:shrink-0"
    />
  );
}
