import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useOutletContext } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { Spinner } from "../../../components/ui";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { PAGE_SIZE } from "../../../lib/paging";
import { useAutosave } from "../../../lib/useAutosave";
import * as reportApi from "../../reports/api/reportApi";
import * as triageApi from "../../triage/api/triageApi";
import * as companiesApi from "../api/companiesApi";
import * as strategyApi from "../api/strategyApi";
import type { CompanyResult, CompanySort, StrategyFilter } from "../api/types";
import { CompanyResultsTable } from "../components/CompanyResultsTable";
import { DEFAULT_COLUMN_VISIBILITY } from "../lib/companyColumns";
import { useColumnVisibility } from "../lib/useColumnVisibility";
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
  const [showFilters, setShowFilters] = useState(true);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [page, setPage] = useState(0);
  const [sort, setSort] = useState<CompanySort>(DEFAULT_SORT);
  const [addingId, setAddingId] = useState<string | null>(null);
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
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
    mutationFn: (name: string) => strategyApi.saveSearch(project.id, name),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(project.id) });
      toast("Search saved");
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
        showFilters={showFilters}
        onToggleFilters={() => setShowFilters((shown) => !shown)}
        query={query}
        onQuery={setQuery}
        onSaveSearch={(name) => saveSearch.mutate(name)}
        onLoadSearch={applyFilter}
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
          <FilterSidebar
            facets={facets.data}
            facetsError={facets.isError}
            filter={filter}
            offLimits={data?.offLimits ?? []}
            onChange={applyFilter}
            onOffLimitsChange={(ids) => offLimitsWrite.mutate(ids)}
          />
        )}

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-5">
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
            totalCount={companies.data?.totalCount ?? 0}
            onPage={setPage}
          />
        </div>
      </div>
    </div>
  );
}
