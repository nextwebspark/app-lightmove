import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner } from "../../../components/ui";
import { useToast } from "../../../components/ui/Toast";
import { useAuth } from "../../auth/AuthProvider";
import { messageFor } from "../../../lib/errorCodes";
import { hasRoomForRails } from "../../../lib/viewport";
import { PAGE_SIZE } from "../../../lib/paging";
import { useAutosave } from "../../../lib/useAutosave";
import * as reportApi from "../../reports/api/reportApi";
import * as triageApi from "../../triage/api/triageApi";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type { CompanyResult, CompanySort, SearchVisibility, StrategyFilter } from "../api/types";
import { CompanyResultsTable } from "../components/CompanyResultsTable";
import { DEFAULT_COLUMN_VISIBILITY } from "../lib/companyColumns";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { useGridSort } from "../../../lib/useGridSort";
import { COMPANY_SORT_FIELDS } from "../lib/companyColumns";
import { FilterSidebar } from "../components/FilterSidebar";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { StrategyToolbar } from "../components/StrategyToolbar";

const DEFAULT_SORT: CompanySort = { field: "employees", direction: "desc" };

export function StrategyPage() {
  const { project } = useOutletContext<ProjectOutletContext>();
  const strategy = useQuery({
    queryKey: strategyApi.STRATEGY_KEY(project.id),
    queryFn: () => strategyApi.getStrategy(project.id),
  });

  if (strategy.isError) {
    return (
      <div className="p-10 text-center font-mono text-[13px] text-text3">
        This mandate&rsquo;s search could not be loaded.
      </div>
    );
  }
  if (!strategy.data) {
    return (
      <div className="grid place-items-center p-16">
        <Spinner />
      </div>
    );
  }
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

  const [filter, setFilter] = useState<StrategyFilter>(() => strategy.data!.filter);
  const [showFilters, setShowFilters] = useState(hasRoomForRails);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useGridSort("strategy", project.id, COMPANY_SORT_FIELDS, DEFAULT_SORT);
  const [addingId, setAddingId] = useState<string | null>(null);
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "strategy",
    project.id,
    DEFAULT_COLUMN_VISIBILITY,
  );

  // A keystroke should narrow the list, not fire a request per character.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  // Any change to what is being asked returns to the first page. Staying on page 4 of a filter that
  // now matches two companies shows an empty table over a non-empty result.
  useEffect(() => setPage(0), [filter, debouncedQuery, sort]);

  const refreshScopedReads = async () => {
    const scopedKeys = [
      strategyApi.STRATEGY_COMPANIES_KEY_PREFIX(project.id),
      triageApi.TRIAGE_KEY_PREFIX(project.id),
      reportApi.REPORT_KEY(project.id),
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
  };

  const companies = useQuery({
    queryKey: strategyApi.STRATEGY_COMPANIES_KEY(project.id, page, PAGE_SIZE, debouncedQuery, sort),
    queryFn: ({ signal }) =>
      strategyApi.getCompanies(project.id, page, PAGE_SIZE, debouncedQuery, sort, signal),
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
   * Off-limits is a separate endpoint and writes immediately rather than through the autosave timer:
   * barring a company is a decision, not a draft, and the list is short enough that every change is
   * deliberate. It invalidates the same scoped reads, since an exclusion changes what the table
   * matches.
   */
  const offLimitsWrite = useMutation({
    mutationFn: async (apolloAccountIds: string[]) => {
      // The response is the whole Strategy and it goes straight into the cache, so a filter edit still
      // sitting in the timer would be overwritten by the copy the server had before it.
      await autosave.flush();
      queryClient.setQueryData(
        strategyApi.STRATEGY_KEY(project.id),
        await strategyApi.putOffLimits(project.id, apolloAccountIds),
      );
      await refreshScopedReads();
    },
    onError: (error) => toast(messageFor(error)),
  });

  const addOne = useMutation({
    mutationFn: (company: CompanyResult) => triageApi.addToUniverse(project.id, company.apolloAccountId),
    onSuccess: (_result, company) => {
      void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });
      toast(`${company.companyName} added to universe`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: () => setAddingId(null),
  });

  const addAll = useMutation({
    mutationFn: async () => {
      // Flush first: "Add all" acts on the *stored* filter, and a debounced edit still in the
      // timer would mean the server adds companies from the filter as it was two chips ago.
      await autosave.flush();
      return triageApi.addAllInScope(project.id);
    },
    onSuccess: (result) => {
      void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });
      toast(
        `Added ${result.added} companies to universe${result.skipped > 0 ? `, ${result.skipped} already there` : ""}`,
      );
    },
    onError: (error) => toast(messageFor(error)),
  });

  const data = strategy.data;

  return (
    /* No negative margins and no viewport arithmetic: the shell gives this tab the whole main area
       and a definite height (FULL_BLEED_TABS in ProjectLayout), so the height is inherited rather
       than guessed from a hard-coded 98px of chrome that any topbar change would falsify. */
    <div className="flex min-h-0 flex-1 flex-col">
      <StrategyToolbar
        filter={filter}
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
        onAiResearch={() => toast("AI research is not available yet")}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        savingSearch={saveSearch.isPending}
        addingAll={addAll.isPending}
      />

      <div className="flex min-h-0 flex-1">
        {showFilters && (
          <>
            <div
              className="fixed inset-0 z-[90] bg-[rgba(15,20,30,0.4)] lg:hidden"
              onClick={() => setShowFilters(false)}
            />
            <FilterSidebar
              facets={facets.data}
              facetsError={facets.isError}
              filter={filter}
              offLimits={data?.offLimits ?? []}
              onChange={applyFilter}
              onOffLimitsChange={(ids) => offLimitsWrite.mutate(ids)}
              onClose={() => setShowFilters(false)}
            />
          </>
        )}

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-3 sm:p-5">
          <CompanyResultsTable
            companies={companies.data?.companies ?? []}
            sort={sort}
            onSortChange={setSort}
            columnVisibility={columnVisibility}
            onColumnVisibilityChange={setColumnVisibility}
            loading={companies.isFetching}
            error={companies.isError}
            onAddToUniverse={(company) => {
              setAddingId(company.apolloAccountId);
              addOne.mutate(company);
            }}
            addingId={addingId}
          />
          <PaginationBar
            page={page}
            size={PAGE_SIZE}
            totalCount={companies.data?.totalCount}
            onPage={setPage}
          />
        </div>
      </div>
    </div>
  );
}
