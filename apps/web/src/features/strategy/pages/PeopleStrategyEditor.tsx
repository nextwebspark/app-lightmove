import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query";
import type { RowSelectionState } from "@tanstack/react-table";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { FilterRailToggle } from "../../../components/ui/FilterRail";
import { EmptyState, FullscreenButton } from "../../../components/ui";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import { SelectionAction, SelectionActionBar } from "../../../components/ui/SelectionActionBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { LeaveGuard } from "../../../components/layout/LeaveGuard";
import { useAutosave } from "../../../lib/useAutosave";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { hasRoomForRails } from "../../../lib/viewport";
import { useAuth } from "../../auth/AuthProvider";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import { TRIAGE_KEY_PREFIX } from "../../triage/api/triageApi";
import type { TriageCompanyStatus } from "../../triage/api/types";
import { TRIAGE_STAGES, stageByStatus } from "../../triage/lib/triageStages";
import * as peopleApi from "../api/peopleApi";
import * as strategyApi from "../api/strategyApi";
import type { PeopleFilter, PeopleSearchPage, PersonResult, SavedSearch, SearchVisibility } from "../api/types";
import { SaveSearchMenu } from "../components/SaveSearchMenu";
import { PeopleFilterSidebar, isEmptyFilter } from "../components/people/PeopleFilterSidebar";
import { PersonPreviewDrawer } from "../components/people/PersonPreviewDrawer";
import { PersonCardGrid } from "../components/people/PersonCardGrid";
import { PersonResultsTable } from "../components/people/PersonResultsTable";
import { samePeopleFilter } from "../lib/peopleFilterIdentity";
import { usePeopleView, type PeopleView } from "../lib/usePeopleView";

/** ContactOut states no page limit; forty pages is the server's own ceiling. */
const MAX_PAGE = 40;

const VIEW_OPTIONS = [
  { value: "table", label: "Table", icon: <Icon d={ICONS.table} size={13} /> },
  { value: "cards", label: "Cards", icon: <Icon d={ICONS.allProjects} size={13} /> },
] as const satisfies readonly { value: PeopleView; label: string; icon: ReactNode }[];

/** What the rail draws until the mandate's stored people filter lands. */
export const NO_PEOPLE_FILTER: PeopleFilter = {
  name: null,
  jobTitles: [],
  titleMatch: null,
  includeRelatedTitles: false,
  recentlyChangedJobs: false,
  excludedJobTitles: [],
  seniorities: [],
  jobFunctions: [],
  skills: [],
  yearsInCurrentRole: [],
  yearsOfExperience: [],
  locations: [],
  locationRadius: null,
  companies: [],
  domains: [],
  companyMatch: null,
  excludedCompanies: [],
  companySizes: [],
  industries: [],
  excludedIndustries: [],
  languages: [],
  education: [],
  keyword: null,
  contactTypes: [],
};

/**
 * Strategy's People mode: the ContactOut filter rail on the left, the people a search returned on the
 * right. The filter autosaves and the count follows it, both free; a search is a deliberate press,
 * because every page not already fetched spends a search credit per person on it. The first press
 * brings the top 25, and Load more the next 25 — ContactOut's own order, the only one it offers.
 *
 * <p>A page is answered from the server's people cache when anybody has asked the same question in the
 * last month, so paging back, reloading or a colleague repeating the search costs nothing — and the
 * screen opens on whatever was already bought for the stored filter, without a press.
 */
export function PeopleStrategyEditor({
  projectId,
  toggle,
}: {
  projectId: string;
  /** The Companies | People control, drawn by whichever mode is on screen. */
  toggle: ReactNode;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const { user } = useAuth();

  const strategy = useQuery({
    queryKey: strategyApi.STRATEGY_KEY(projectId),
    queryFn: () => strategyApi.getStrategy(projectId),
  });
  const facets = useQuery({
    queryKey: peopleApi.PEOPLE_FACETS_KEY,
    queryFn: peopleApi.getPeopleFacets,
    staleTime: Infinity,
  });

  const [filter, setFilter] = useState<PeopleFilter>(() => strategy.data?.peopleFilter ?? NO_PEOPLE_FILTER);
  const [showFilters, setShowFilters] = useState(hasRoomForRails);
  const [run, setRun] = useState(0);
  const [rowSelection, setRowSelection] = useState<RowSelectionState>({});
  const [previewed, setPreviewed] = useState<PersonResult | null>(null);
  const [isFullscreen, toggleFullscreen] = useFullscreen();
  const [view, setView] = usePeopleView();

  // Adopted the once, for StrategyEditor's reason: a later response would stomp chips clicked since.
  const hasAdoptedStoredFilter = useRef(strategy.data !== undefined);
  useEffect(() => {
    if (hasAdoptedStoredFilter.current || !strategy.data) return;
    hasAdoptedStoredFilter.current = true;
    setFilter(strategy.data.peopleFilter);
  }, [strategy.data]);

  const filterWrite = useMutation({
    mutationKey: strategyApi.STRATEGY_WRITE_KEY(projectId),
    mutationFn: async (payload: PeopleFilter) => {
      queryClient.setQueryData(strategyApi.STRATEGY_KEY(projectId), await peopleApi.putPeopleFilter(projectId, payload));
      await queryClient.invalidateQueries({ queryKey: peopleApi.PEOPLE_COUNT_KEY(projectId) });
    },
  });
  const autosave = useAutosave<PeopleFilter>((payload) => filterWrite.mutateAsync(payload), {
    onError: (error) => toast.error(messageFor(error)),
  });

  const applyFilter = (next: PeopleFilter) => {
    setFilter(next);
    autosave.schedule(next);
  };

  const count = useQuery({
    queryKey: peopleApi.PEOPLE_COUNT_KEY(projectId),
    queryFn: ({ signal }) => peopleApi.getPeopleCount(projectId, signal),
    placeholderData: keepPreviousData,
  });

  // Seeds run 0 rather than mirroring into state: the grid, Load more and the held marks then read one
  // result set whether it was just bought or read back.
  const restored = useQuery({
    queryKey: peopleApi.PEOPLE_RESULTS_KEY(projectId),
    queryFn: async () => {
      const { pages } = await peopleApi.getPeopleResults(projectId);
      const key = peopleApi.PEOPLE_SEARCH_KEY(projectId, 0);
      if (pages.length === 0) {
        // A set read back on an earlier visit is for a filter that has changed since.
        await queryClient.resetQueries({ queryKey: key, exact: true });
      } else {
        queryClient.setQueryData<InfiniteData<PeopleSearchPage, number>>(key, {
          pages,
          pageParams: pages.map((page) => page.page),
        });
      }
      return pages.length;
    },
    enabled: run === 0,
    staleTime: 0,
    gcTime: 0,
  });

  const results = useInfiniteQuery({
    queryKey: peopleApi.PEOPLE_SEARCH_KEY(projectId, run),
    queryFn: ({ pageParam }) => peopleApi.searchPeople(projectId, pageParam),
    initialPageParam: 1,
    getNextPageParam: (last, pages) => {
      const loaded = pages.reduce((sum, page) => sum + page.people.length, 0);
      return loaded < last.total && last.people.length > 0 && last.page < MAX_PAGE ? last.page + 1 : undefined;
    },
    enabled: run > 0,
    gcTime: Infinity,
    // A retry or a refetch on focus is a second bill for a page not yet cached; neither happens here.
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    if (results.error) toast.error(messageFor(results.error));
  }, [results.error, toast]);

  const people = results.data?.pages.flatMap((page) => page.people) ?? [];
  const total = results.data?.pages[0]?.total ?? 0;
  const billed = results.data?.pages.reduce((sum, page) => sum + page.billed, 0) ?? 0;

  const handleSearch = async () => {
    // The server searches the stored filter, so the last chip click must reach it first — and a
    // search is billed, so one over a filter the server never took is not run at all.
    try {
      await autosave.flush();
    } catch (error) {
      toast.error(messageFor(error));
      return;
    }
    setRowSelection({});
    setRun((current) => current + 1);
  };

  const addPeople = useMutation({
    mutationFn: ({ slugs, status }: { slugs: string[]; status: TriageCompanyStatus }) =>
      peopleApi.addPeople(projectId, slugs, status),
    onSuccess: (result, { status }) => {
      const filedAs = new Map(result.filed.map((filed) => [filed.linkedinSlug.toLowerCase(), filed.candidateId]));
      const stamp = (person: PersonResult): PersonResult => {
        const candidateId = filedAs.get(person.linkedinSlug.toLowerCase());
        return candidateId ? { ...person, held: true, candidateId } : person;
      };
      // Marked held in place rather than refetched: a refetch re-asks every page, free but not idle.
      queryClient.setQueryData<InfiniteData<PeopleSearchPage>>(peopleApi.PEOPLE_SEARCH_KEY(projectId, run), (data) =>
        data && {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            people: page.people.map(stamp),
          })),
        },
      );
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      void queryClient.invalidateQueries({ queryKey: TRIAGE_KEY_PREFIX(projectId) });
      setRowSelection({});
      // Left open on the person just filed: their Contact fold is now the one that finds an email.
      setPreviewed((open) => open && stamp(open));
      // Mirrors the Companies bar: an employer the mandate already holds keeps its stage, and saying
      // so is the difference between "moved" and "joined it where it was".
      toast(
        `${result.added} ${result.added === 1 ? "person" : "people"} added to ${stageByStatus(status).label}` +
          (result.elsewhere > 0 ? `, ${result.elsewhere} at a company already at another stage` : "") +
          (result.skipped > 0 ? `, ${result.skipped} already in this mandate` : "") +
          (result.unavailable > 0 ? `, ${result.unavailable} need a fresh search` : ""),
      );
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const saveSearch = useMutation({
    mutationFn: async ({ name, visibility }: { name: string; visibility: SearchVisibility }) => {
      await autosave.flush();
      return strategyApi.saveSearch(projectId, name, visibility, "PEOPLE");
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) });
      toast("Search saved");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const editSearch = useMutation({
    mutationFn: ({ searchId, ...patch }: { searchId: string; name?: string; visibility?: SearchVisibility }) =>
      strategyApi.patchSearch(projectId, searchId, patch),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) }),
    onError: (error) => toast.error(messageFor(error)),
  });
  const overwriteSearch = useMutation({
    mutationFn: async (searchId: string) => {
      await autosave.flush();
      return strategyApi.overwriteSearch(projectId, searchId);
    },
    onSuccess: (search) => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) });
      toast(`${search.name} updated`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const deleteSearch = useMutation({
    mutationFn: (searchId: string) => strategyApi.deleteSearch(projectId, searchId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) });
      toast("Search deleted");
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (strategy.isError) {
    return <div className="p-10 text-center text-note text-u-text3">This mandate&rsquo;s search could not be loaded.</div>;
  }

  const renderResults = (shown: PersonResult[], loading: boolean) =>
    view === "cards" ? (
      <PersonCardGrid
        people={shown}
        loading={loading}
        total={total}
        rowSelection={rowSelection}
        onRowSelectionChange={setRowSelection}
        onOpen={setPreviewed}
      />
    ) : (
      <PersonResultsTable
        people={shown}
        loading={loading}
        rowSelection={rowSelection}
        onRowSelectionChange={setRowSelection}
        onOpen={setPreviewed}
      />
    );

  const peopleSearches = (strategy.data?.searches ?? []).filter((search) => search.kind === "PEOPLE");
  const selectedSlugs = Object.keys(rowSelection);

  return (
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <LeaveGuard hasUnsavedChanges={autosave.hasUnsavedChanges} flush={autosave.flush} />
      <div className="flex min-h-[44px] flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border bg-u-raised px-3 py-2 sm:px-5 sm:py-1.5">
        {toggle}
        <SaveSearchMenu
          searches={peopleSearches}
          isActive={(search: SavedSearch) => search.peopleFilter !== null && samePeopleFilter(filter, search.peopleFilter)}
          viewerId={user?.id ?? null}
          onSave={(name, visibility) => saveSearch.mutate({ name, visibility })}
          onLoad={(search) => search.peopleFilter && applyFilter(search.peopleFilter)}
          onRename={(searchId, name) => editSearch.mutate({ searchId, name })}
          onSetVisibility={(searchId, visibility) => editSearch.mutate({ searchId, visibility })}
          onOverwrite={(searchId) => overwriteSearch.mutate(searchId)}
          onDelete={(searchId) => deleteSearch.mutate(searchId)}
          saving={saveSearch.isPending}
        />
        <FilterRailToggle open={showFilters} onToggle={() => setShowFilters((shown) => !shown)} />
        <SegmentedControl
          label="View"
          options={VIEW_OPTIONS}
          value={view}
          onChange={setView}
          className="hidden md:inline-flex"
        />
        {results.data && (
          <span className="text-meta text-u-text3 sm:ml-auto">
            {billed > 0 ? `${billed} search credits spent on this search` : "Answered from the cache — no credits spent"}
          </span>
        )}
      </div>

      <div className="flex min-h-0 flex-1">
        {showFilters && (
          <PeopleFilterSidebar
            facets={facets.data}
            facetsError={facets.isError}
            filter={filter}
            count={count.data}
            countPending={count.isFetching || autosave.status === "saving"}
            onChange={applyFilter}
            onSearch={() => void handleSearch()}
            searching={results.isFetching && !results.isFetchingNextPage}
            onClose={() => setShowFilters(false)}
          />
        )}

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-2">
          <div className="flex min-h-0 flex-1 flex-col">
            {(run === 0 && restored.isFetching) || (results.isFetching && !results.data) ? (
              renderResults([], true)
            ) : !results.data ? (
              <EmptyState
                icon={<Icon d={ICONS.members} size={22} />}
                title="Search ContactOut for people"
                body={
                  isEmptyFilter(filter)
                    ? "Build a filter on the left — the count is free and follows every change."
                    : "Press Search to bring in the top 25. A page already fetched by anyone is free."
                }
              />
            ) : people.length === 0 ? (
              <EmptyState
                icon={<Icon d={ICONS.search} size={22} />}
                title="Nobody matched"
                body="Loosen the filter and search again — a search finding nobody spends nothing."
              />
            ) : (
              renderResults(people, results.isFetchingNextPage)
            )}
            {selectedSlugs.length > 0 && (
              <SelectionActionBar
                count={selectedSlugs.length}
                noun="person"
                plural="people"
                onClear={() => setRowSelection({})}
              >
                {TRIAGE_STAGES.map((stage) => (
                  <SelectionAction
                    key={stage.status}
                    icon={stage.icon}
                    label={stage.label}
                    tone={stage.status === "declined" ? "danger" : "neutral"}
                    disabled={addPeople.isPending}
                    onClick={() => addPeople.mutate({ slugs: selectedSlugs, status: stage.status })}
                  />
                ))}
              </SelectionActionBar>
            )}
          </div>
          <div className="flex items-center justify-between gap-3 px-1">
            <span className="text-meta text-u-text3">
              {results.data ? `Showing ${people.length.toLocaleString()} of ${total.toLocaleString()}` : ""}
            </span>
            <div className="flex items-center gap-3">
              {results.hasNextPage && (
                <button
                  type="button"
                  onClick={() => void results.fetchNextPage()}
                  disabled={results.isFetchingNextPage}
                  className="rounded-[6px] border border-u-border-strong px-3 py-1.5 text-note font-medium text-u-text2 transition hover:text-u-text disabled:opacity-40"
                >
                  {results.isFetchingNextPage ? "Loading…" : "Load 25 more · up to 25 credits"}
                </button>
              )}
              <FullscreenButton active={isFullscreen} onToggle={toggleFullscreen} />
            </div>
          </div>
        </div>
      </div>
      <PersonPreviewDrawer
        projectId={projectId}
        person={previewed}
        onClose={() => setPreviewed(null)}
        onAdd={(person, status) => addPeople.mutate({ slugs: [person.linkedinSlug], status })}
        adding={addPeople.isPending}
      />
    </div>
  );
}
