import {
  keepPreviousData,
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
} from "@tanstack/react-query";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { EmptyState, FullscreenButton } from "../../../components/ui";
import { SelectionAction, SelectionActionBar } from "../../../components/ui/SelectionActionBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { useAutosave } from "../../../lib/useAutosave";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { hasRoomForRails } from "../../../lib/viewport";
import { useAuth } from "../../auth/AuthProvider";
import { CANDIDATES_KEY_PREFIX } from "../../candidates/api/candidatesApi";
import { TRIAGE_KEY_PREFIX } from "../../triage/api/triageApi";
import * as peopleApi from "../api/peopleApi";
import * as strategyApi from "../api/strategyApi";
import type { PeopleFilter, PeopleSearchPage, SavedSearch, SearchVisibility } from "../api/types";
import { SaveSearchMenu } from "../components/SaveSearchMenu";
import { PeopleFilterSidebar, isEmptyFilter } from "../components/people/PeopleFilterSidebar";
import { PersonResultsTable } from "../components/people/PersonResultsTable";
import { samePeopleFilter } from "../lib/peopleFilterIdentity";

/** ContactOut states no page limit; forty pages is the server's own ceiling. */
const MAX_PAGE = 40;

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
 * last month, so paging back, reloading or a colleague repeating the search costs nothing.
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
  const [selected, setSelected] = useState<Set<string>>(() => new Set());
  const [isFullscreen, toggleFullscreen] = useFullscreen();

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
      try {
        queryClient.setQueryData(strategyApi.STRATEGY_KEY(projectId), await peopleApi.putPeopleFilter(projectId, payload));
        await queryClient.invalidateQueries({ queryKey: peopleApi.PEOPLE_COUNT_KEY(projectId) });
      } catch (error) {
        toast(messageFor(error));
        throw error;
      }
    },
  });
  const autosave = useAutosave<PeopleFilter>((payload) => filterWrite.mutateAsync(payload));

  const applyFilter = (next: PeopleFilter) => {
    setFilter(next);
    autosave.schedule(next);
  };

  const count = useQuery({
    queryKey: peopleApi.PEOPLE_COUNT_KEY(projectId),
    queryFn: ({ signal }) => peopleApi.getPeopleCount(projectId, signal),
    placeholderData: keepPreviousData,
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
    // A retry or a refetch on focus is a second bill for a page not yet cached; neither happens here.
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  });

  useEffect(() => {
    if (results.error) toast(messageFor(results.error));
  }, [results.error, toast]);

  const people = results.data?.pages.flatMap((page) => page.people) ?? [];
  const total = results.data?.pages[0]?.total ?? 0;
  const billed = results.data?.pages.reduce((sum, page) => sum + page.billed, 0) ?? 0;

  const handleSearch = async () => {
    // The server searches the stored filter, so the last chip click must reach it first.
    await autosave.flush();
    setSelected(new Set());
    setRun((current) => current + 1);
  };

  const addPeople = useMutation({
    mutationFn: (slugs: string[]) => peopleApi.addPeople(projectId, slugs),
    onSuccess: (result, slugs) => {
      // Marked held in place rather than refetched: a refetch re-asks every page, free but not idle.
      queryClient.setQueryData<InfiniteData<PeopleSearchPage>>(peopleApi.PEOPLE_SEARCH_KEY(projectId, run), (data) =>
        data && {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            people: page.people.map((person) =>
              slugs.includes(person.linkedinSlug) ? { ...person, held: true } : person,
            ),
          })),
        },
      );
      void queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY_PREFIX(projectId) });
      void queryClient.invalidateQueries({ queryKey: TRIAGE_KEY_PREFIX(projectId) });
      setSelected(new Set());
      toast(
        `${result.added} ${result.added === 1 ? "person" : "people"} added to the universe` +
          (result.skipped > 0 ? `, ${result.skipped} already there` : "") +
          (result.unavailable > 0 ? `, ${result.unavailable} need a fresh search` : ""),
      );
    },
    onError: (error) => toast(messageFor(error)),
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
    onError: (error) => toast(messageFor(error)),
  });
  const editSearch = useMutation({
    mutationFn: ({ searchId, ...patch }: { searchId: string; name?: string; visibility?: SearchVisibility }) =>
      strategyApi.patchSearch(projectId, searchId, patch),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) }),
    onError: (error) => toast(messageFor(error)),
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
    onError: (error) => toast(messageFor(error)),
  });
  const deleteSearch = useMutation({
    mutationFn: (searchId: string) => strategyApi.deleteSearch(projectId, searchId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: strategyApi.STRATEGY_KEY(projectId) });
      toast("Search deleted");
    },
    onError: (error) => toast(messageFor(error)),
  });

  if (strategy.isError) {
    return <div className="p-10 text-center text-note text-u-text3">This mandate&rsquo;s search could not be loaded.</div>;
  }

  const peopleSearches = (strategy.data?.searches ?? []).filter((search) => search.kind === "PEOPLE");
  const toggleOne = (slug: string) =>
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(slug)) next.delete(slug);
      else next.add(slug);
      return next;
    });
  const toggleAll = (slugs: string[]) =>
    setSelected((current) => (slugs.every((slug) => current.has(slug)) ? new Set() : new Set(slugs)));

  return (
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
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
        <button
          type="button"
          onClick={() => setShowFilters((shown) => !shown)}
          aria-expanded={showFilters}
          className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] p-2 text-note text-u-text3 transition hover:bg-u-surface hover:text-u-text"
        >
          <Icon d="M3 4h18l-7 8v6l-4 2v-8L3 4Z" size={14} className="flex-none" />
          {showFilters ? "Hide Filters" : "Show Filters"}
        </button>
        {results.data && (
          <span className="text-meta text-u-text3 sm:ml-auto">
            {billed > 0 ? `${billed} search credits spent on this search` : "Answered from the cache — no credits spent"}
          </span>
        )}
      </div>

      <div className="flex min-h-0 flex-1">
        {showFilters && (
          <>
            <div className="fixed inset-0 z-[90] bg-u-scrim lg:hidden" onClick={() => setShowFilters(false)} />
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
          </>
        )}

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-2">
          <div className="relative flex min-h-0 flex-1 flex-col">
            {run === 0 || (!results.data && !results.isFetching) ? (
              <EmptyState
                icon={<Icon d={ICONS.members} size={22} />}
                title="Search ContactOut for people"
                body={
                  isEmptyFilter(filter)
                    ? "Build a filter on the left — the count is free and follows every change."
                    : "Press Search to bring in the top 25. A page already fetched by anyone is free."
                }
              />
            ) : results.isFetching && !results.data ? (
              <div className="flex-1 animate-pulse rounded-[8px] border border-u-border bg-u-surface" />
            ) : people.length === 0 ? (
              <EmptyState
                icon={<Icon d={ICONS.search} size={22} />}
                title="Nobody matched"
                body="Loosen the filter and search again — a search finding nobody spends nothing."
              />
            ) : (
              <PersonResultsTable people={people} selected={selected} onToggle={toggleOne} onToggleAll={toggleAll} />
            )}
            {selected.size > 0 && (
              <SelectionActionBar
                count={selected.size}
                noun="person"
                plural="people"
                onClear={() => setSelected(new Set())}
              >
                <SelectionAction
                  icon={ICONS.plus}
                  label="Add to universe"
                  disabled={addPeople.isPending}
                  onClick={() => addPeople.mutate([...selected])}
                />
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
    </div>
  );
}
