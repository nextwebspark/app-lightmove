import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState } from "react";
import { Navigate, useOutletContext, useParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { FullscreenButton } from "../../../components/ui";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { DEFAULT_PAGE_SIZE } from "../../../lib/paging";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { useGridSort, type GridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidatesPage, CandidateStatus } from "../../candidates/api/types";
import {
  CandidateDrawer,
  type CandidateCompanyContext,
} from "../../candidates/components/CandidateDrawer";
import { RemoveCandidateDialog } from "../../candidates/components/RemoveCandidateDialog";
import { CANDIDATE_STATUSES } from "../../candidates/lib/candidateVocabulary";
import { useChangeCandidateStatus } from "../../candidates/lib/useChangeCandidateStatus";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import type { CustomColumn } from "../../customcolumns/api/types";
import * as positionApi from "../../position/api/positionApi";
import { canExecuteProjectWork } from "../../projects/lib/access";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import type * as talentMapTypes from "../../talentmap/api/types";
import { TalentMapView } from "../../talentmap/components/TalentMapView";
import { useTalentMapPreferences } from "../../talentmap/lib/useTalentMapPreferences";
import * as exportApi from "../api/exportApi";
import * as triageApi from "../api/triageApi";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import { CompanyDrawer } from "../components/CompanyDrawer";
import { ImportSpreadsheetDialog } from "../components/ImportSpreadsheetDialog";
import { ManageColumnsDialog } from "../components/ManageColumnsDialog";
import { RemoveCompanyDialog } from "../components/RemoveCompanyDialog";
import { TriageCompanyTable } from "../components/TriageCompanyTable";
import { TriageToolbar } from "../components/TriageToolbar";
import {
  createTriageCompanyColumns,
  defaultTriageColumnVisibility,
  TRIAGE_SORT_FIELDS,
} from "../lib/triageCompanyColumns";
import { awaitingResearch, toTriageRows } from "../lib/triageRows";
import { stageBySlug, TRIAGE_STAGES } from "../lib/triageStages";
import { useProjectStream, type ProjectStreamKind } from "../lib/useProjectStream";
import { useSaveCompanyNote } from "../lib/useSaveCompanyNote";

/**
 * The grid's built-in columns for the layout hook. A mandate's own custom columns are deliberately
 * absent: `useGridLayout` is keyed per user and per grid, *not* per project — a column layout is a
 * working habit rather than a property of one mandate — so remembering a width for a column only one
 * project has would carry a phantom into every other project's grid.
 */
const TRIAGE_LAYOUT_COLUMNS = layoutColumnsOf(createTriageCompanyColumns([]));

/** A stable empty array: a fresh `[]` per render would rebuild every column def on every render. */
const EMPTY_CUSTOM_COLUMNS: CustomColumn[] = [];

/** How hard the In-universe screen looks for research landing on a fresh plugin capture. */
const RESEARCH_POLL_MS = 4_000;

/** How often the map asks again while the server is still placing rows it could not place yet. */
const GEOCODING_POLL_MS = 3_000;

/** Newest first, matching the server's default, so the first paint is not a re-sort. */
const DEFAULT_SORT: GridSort<TriageSortField> = { field: "added", direction: "desc" };

/** Where a move sends a company, in words, for the toast that confirms it. */
const MOVE_LABELS: Record<TriageCompanyStatus, string> = {
  inUniverse: "the universe",
  shortlisted: "the shortlist",
  declined: "declined",
};

/**
 * One stage of a mandate's companies — In universe, Shortlisted or Declined — as its own page, in the
 * same grid Strategy uses.
 *
 * <p>The stage comes from the URL rather than a prop, so all three are one route pattern and a stage
 * added to {@link TRIAGE_STAGES} is reachable without touching the router. An unknown slug redirects
 * rather than rendering an empty grid for a stage that does not exist.
 *
 * <p>A mandate nobody has triaged shows an empty stage rather than the market. That is the change
 * this screen went through: discovery moved to Strategy, and what remains here is the record of
 * decisions, which starts empty and stays that way until someone makes one.
 */
export function TriageStagePage() {
  const { stage: stageSlug } = useParams();
  const { project } = useOutletContext<ProjectOutletContext>();
  const stage = stageSlug ? stageBySlug(stageSlug) : undefined;

  if (!stage) {
    return <Navigate to={`/projects/${project.id}/companies/${TRIAGE_STAGES[0].slug}`} replace />;
  }
  // Keyed on the project too, not just the stage: the outlet context updates in place on a project
  // switch (react-router does not remount a route element just because a param changed), so without
  // this a mandate switch while staying on the same stage tab would carry the previous mandate's
  // search box, page number and seen-executive-statuses filter options into the new one.
  return <TriageStage key={`${project.id}:${stage.slug}`} />;
}

function TriageStage() {
  const { stage: stageSlug } = useParams();
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const rowsChanged = useProjectRowsChanged();
  const toast = useToast();

  const stage = stageBySlug(stageSlug!)!;
  const canWrite = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  /** The Executive column's own header filter — independent of the Company one above it. */
  const [executiveQuery, setExecutiveQuery] = useState("");
  const [debouncedExecutiveQuery, setDebouncedExecutiveQuery] = useState("");
  /** The Status column's own header filter — a closed checkbox set, applied with no debounce. */
  const [executiveStatuses, setExecutiveStatuses] = useState<string[]>([]);
  /**
   * Every row id with a write in flight — a company's move or no-executive-found flag, or a
   * candidate's status change — so each row's own buttons disable independently. A single shared id
   * here would let one row's mutation settling re-enable a different row still mid-flight, since the
   * second write's start would already have overwritten the first row's id.
   */
  const [busyIds, setBusyIds] = useState<ReadonlySet<string>>(() => new Set());
  const markBusy = (id: string) =>
    setBusyIds((current) => (current.has(id) ? current : new Set(current).add(id)));
  const clearBusy = (id: string) =>
    setBusyIds((current) => {
      if (!current.has(id)) return current;
      const next = new Set(current);
      next.delete(id);
      return next;
    });
  const [openCompany, setOpenCompany] = useState<OpenCompany | null>(null);
  const [pendingRemoval, setPendingRemoval] = useState<TriageCompany | null>(null);
  const [profile, setProfile] = useState<OpenProfile | null>(null);
  const profileCompany = profile?.company ?? null;
  const [pendingCandidateRemoval, setPendingCandidateRemoval] = useState<Candidate | null>(null);
  const [importing, setImporting] = useState(false);
  const [managingColumns, setManagingColumns] = useState(false);
  /** Set when "Edit field" is opened from a header menu, so the dialog lands already renaming it. */
  const [editColumnId, setEditColumnId] = useState<string | null>(null);
  const [sort, setSort] = useGridSort("companies", project.id, TRIAGE_SORT_FIELDS, DEFAULT_SORT);
  const [isFullscreen, toggleFullscreen] = useFullscreen();
  const [mapPreferences, setMapPreferences] = useTalentMapPreferences(project.id);

  /**
   * Whether this deployment draws a map at all. A refused or failed read means no toggle rather than
   * a broken globe — the grid is the screen, the map is the second reading of it. Read once and kept:
   * a token does not change while a tab is open.
   */
  const mapConfig = useQuery({
    queryKey: talentMapApi.TALENT_MAP_CONFIG_KEY,
    queryFn: ({ signal }) => talentMapApi.getTalentMapConfig(signal),
    staleTime: Infinity,
  });
  const mapOffered =
    stage.status === "inUniverse" && mapConfig.data?.enabled === true && !!mapConfig.data.publicToken;
  const view = mapOffered ? mapPreferences.view : "table";

  /**
   * The mandate's currency, offered to a new executive's package so a consultant stops picking it on
   * every person. Only for a seat that can add one: this is a read that persists nothing, and a
   * client seat has no Add executive button to default anything for.
   */
  const briefCompensation = useQuery({
    queryKey: positionApi.POSITION_COMPENSATION_KEY(project.id),
    queryFn: ({ signal }) => positionApi.getBriefCompensation(project.id, signal),
    enabled: canWrite,
    staleTime: Infinity,
  });

  /**
   * The mandate's own extra columns. Read once for the screen and shared by the grid, the toolbar's
   * picker and both drawers' edit forms — a column is one fact about the project, not one per widget.
   */
  const customColumnsQuery = useQuery({
    queryKey: customColumnsApi.CUSTOM_COLUMNS_KEY(project.id),
    queryFn: () => customColumnsApi.getCustomColumns(project.id),
  });
  const customColumns = useMemo(
    () => customColumnsQuery.data?.columns ?? EMPTY_CUSTOM_COLUMNS,
    [customColumnsQuery.data],
  );
  const defaultVisibility = useMemo(
    () => defaultTriageColumnVisibility(customColumns),
    [customColumns],
  );
  // Split by which half of the row they describe: a company's form has no business offering the
  // person's columns, and vice versa.
  const companyColumns = useMemo(
    () => customColumns.filter((column) => column.target === "company"),
    [customColumns],
  );
  const candidateColumns = useMemo(
    () => customColumns.filter((column) => column.target === "candidate"),
    [customColumns],
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    // Bumped from "companies" when Source became hidden-by-default, and again to v3 when the Email
    // and Phone columns arrived hidden. `useColumnVisibility` merges a stored map *over* the
    // defaults — deliberately, so a column added later takes its declared default — which means
    // everyone who had opened this grid already had `source: true` written down and would never
    // have seen the new default. A new namespace resets this grid's remembered layout once, which
    // is the price of a default that otherwise could not take effect.
    "companies.v3",
    project.id,
    defaultVisibility,
  );
  const [layout, setLayout] = useGridLayout("companies", TRIAGE_LAYOUT_COLUMNS);

  // A keystroke should narrow the list, not fire a request per character.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  useEffect(() => {
    const timer = setTimeout(() => setDebouncedExecutiveQuery(executiveQuery), 300);
    return () => clearTimeout(timer);
  }, [executiveQuery]);

  // Any change to what is being asked returns to the first page. Staying on page 4 of a search that
  // now matches two companies shows an empty grid over a non-empty result.
  useEffect(
    () => setPage(0),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [debouncedQuery, debouncedExecutiveQuery, executiveStatuses.join(","), sort],
  );

  /**
   * Every write invalidates the whole prefix rather than this stage's key. A move changes two stages
   * and all three counts, and a page that refreshed only the list it was looking at would show the
   * company gone and the sidebar's shortlist badge still one short.
   */
  const refreshEveryStage = () =>
    void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });

  const refreshPeople = () =>
    void queryClient.invalidateQueries({
      queryKey: candidatesApi.CANDIDATES_KEY_PREFIX(project.id),
    });

  const refreshMap = () =>
    void queryClient.invalidateQueries({
      queryKey: talentMapApi.TALENT_MAP_KEY_PREFIX(project.id),
    });

  /**
   * Removing a company unmaps its people rather than deleting them, and adding one changes which
   * people the grid should be asking about — so the two caches move together on every write, which
   * is what {@link useProjectRowsChanged} is. The columns are not part of that and stay here: only
   * an import defines one, and refreshing rows without their headers leaves the imported values in
   * columns the grid does not yet know how to render.
   */
  const refreshEverything = () => {
    void rowsChanged(project.id);
    void queryClient.invalidateQueries({
      queryKey: customColumnsApi.CUSTOM_COLUMNS_KEY(project.id),
    });
  };

  /**
   * A write is one action and refreshes everything; an announcement says what moved, so it refreshes
   * that. One capture is announced up to three times — the capture itself, the research landing, and
   * the employer being filed into the universe and researched in turn — and refetching the whole
   * screen for each of them is where the grid's visible churn came from. The columns stay out of it
   * entirely: only an import defines one, and the import dialog refreshes them itself.
   */
  const refreshWhatMoved = (kinds: ProjectStreamKind[]) => {
    if (kinds.some((kind) => kind.startsWith("company-"))) {
      refreshEveryStage();
    }
    if (kinds.some((kind) => kind.startsWith("candidate-"))) {
      refreshPeople();
    }
    // Both halves are points on the globe. Free while the grid is the view: the map's reads are
    // disabled there, and an inactive query is marked stale rather than refetched.
    refreshMap();
  };

  const streamIsLive = useProjectStream(project.id, refreshWhatMoved);

  /**
   * The screen's ordinary freshness is the project stream above: the server says when something
   * under the mandate moved, and the grid refetches. This poll is the degraded mode, and only that —
   * while the stream is down *and* a visible plugin capture is still being researched, the grid
   * looks for itself every few seconds. With the stream up it announces the same research within a
   * second, so polling beside it was one wasted page read per tick, per open tab.
   *
   * <p>A ref rather than the queries themselves: react-query evaluates this while the first query is
   * still being declared, so reading the people queries here directly is a use-before-init. The ref
   * is refreshed right after they land, below.
   */
  const visiblePeople = useRef<Candidate[]>([]);
  const researchPoll = () => {
    if (streamIsLive || stage.status !== "inUniverse") return false;
    return awaitingResearch(visiblePeople.current) ? RESEARCH_POLL_MS : false;
  };

  const companies = useQuery({
    queryKey: triageApi.TRIAGE_KEY(
      project.id,
      stage.status,
      page,
      pageSize,
      debouncedQuery,
      debouncedExecutiveQuery,
      executiveStatuses,
      sort,
    ),
    queryFn: ({ signal }) =>
      triageApi.getTriageCompanies(
        project.id,
        stage.status,
        page,
        pageSize,
        debouncedQuery,
        debouncedExecutiveQuery,
        executiveStatuses,
        sort,
        signal,
      ),
    // The grid's reads are the grid's: the map reads the whole stage in one request of its own.
    enabled: view === "table",
    // Paging without blanking the grid, which would make every page turn look like a reload.
    placeholderData: keepPreviousData,
    refetchInterval: researchPoll,
  });

  const companyIds = useMemo(
    () => (companies.data?.companies ?? []).map((company) => company.id),
    [companies.data],
  );

  /**
   * The people at the companies on this page, asked for separately rather than embedded in the list
   * above. `triagecompany` knows nothing about candidates by design — the dependency runs one way —
   * so the grid composes the two sides here instead of either feature learning the other's storage.
   *
   * <p>Disabled until the companies land: firing with no ids would either ask for the whole mandate
   * or answer nothing, and both are wrong for a page that does not exist yet.
   *
   * <p>No size is named. The server sizes a company-filtered read at its own ceiling, which is a
   * number this side must not try to guess — see {@link candidatesApi.getCandidates}. The
   * Executive-name filter goes with it for the same reason: matched on the arrived page alone, a
   * company whose only match sat past that ceiling would draw the "no executive mapped" slot over
   * someone it has.
   */
  const executiveName = debouncedExecutiveQuery.trim();
  const mappedScope = { triageCompanyIds: companyIds, query: executiveName };
  const mappedPeople = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(project.id, mappedScope),
    queryFn: ({ signal }) => candidatesApi.getCandidates(project.id, mappedScope, signal),
    enabled: view === "table" && companyIds.length > 0,
    placeholderData: keepPreviousData,
    refetchInterval: researchPoll,
  });

  /**
   * What each read is entitled to show. `enabled` stops a fetch and never the cache behind it, and
   * `keepPreviousData` hands an uncached key the last page outright — so a gate that lives on the
   * query alone leaks a stale page into a view that asked for nothing like it.
   */
  const mappedPage = companyIds.length > 0 ? mappedPeople.data : undefined;

  const totalCount = companies.data?.totalCount;
  const lastPage = Math.max(0, Math.ceil((totalCount ?? 0) / pageSize) - 1);

  /**
   * Whether this render carries the employer-less executives at all — one flag for the read <i>and</i>
   * the merge below, because `enabled` stops the fetch and not the data: gated only there, the cached
   * page kept being merged into every filtered view. A company-name filter hides them; the Executive
   * and Status filters narrow them.
   */
  const showsUnmappedPeople =
    view === "table" && stage.status === "inUniverse" && !debouncedQuery.trim() && page === lastPage;

  /**
   * Executives whose employer is not in the mandate's universe at all. They belong to the mandate
   * rather than to any company, so they sit after the companies on the universe's last page — the one
   * place a reader reaches by scrolling to the end of the mapping. Grouping the grid by company is
   * where they eventually get a heading of their own; until then, invisible would be worse.
   *
   * <p>The name filter goes to the server: the read is capped there, so matching it on the arrived
   * page alone would miss everyone past the cap.
   */
  const unmappedScope = { unmapped: true, query: executiveName };
  const unmappedPeople = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(project.id, unmappedScope),
    queryFn: ({ signal }) => candidatesApi.getCandidates(project.id, unmappedScope, signal),
    enabled: showsUnmappedPeople,
    placeholderData: keepPreviousData,
    refetchInterval: researchPoll,
  });
  const unmappedPage = showsUnmappedPeople ? unmappedPeople.data : undefined;

  visiblePeople.current = [
    ...(mappedPage?.candidates ?? []),
    ...(unmappedPage?.candidates ?? []),
  ];

  /**
   * Which Status values the Status column's header menu offers — only the ones actually borne by an
   * executive the mandate has mapped, so a mandate with just Identified and Contacted people never
   * sees the other five sitting there unusable. Learned only from an unfiltered read (the component
   * remounts fresh per project and stage, so the first page is always one): once either header filter
   * narrows what loads, that narrower set must not overwrite the true one the checkboxes describe.
   */
  const [seenExecutiveStatuses, setSeenExecutiveStatuses] = useState<Set<CandidateStatus>>(
    () => new Set(),
  );
  useEffect(() => {
    if (executiveStatuses.length > 0 || executiveName || visiblePeople.current.length === 0) return;
    setSeenExecutiveStatuses((current) => {
      const next = new Set(current);
      for (const candidate of visiblePeople.current) next.add(candidate.status);
      return next.size === current.size ? current : next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mappedPage, unmappedPage, executiveStatuses.length, executiveName]);
  const executiveStatusOptions = useMemo(
    () =>
      CANDIDATE_STATUSES.filter((status) => seenExecutiveStatuses.has(status.value)).map(
        (status) => ({ value: status.value, label: status.label }),
      ),
    [seenExecutiveStatuses],
  );

  /** The whole stage as points, read once when the globe opens and again when the mandate changes. */
  const talentMap = useQuery({
    queryKey: talentMapApi.TALENT_MAP_KEY(project.id, stage.status),
    queryFn: ({ signal }) => talentMapApi.getTalentMap(project.id, stage.status, signal),
    enabled: view === "map",
    placeholderData: keepPreviousData,
  });

  /**
   * The points on their own, polled while the server is still placing rows it could not place in one
   * read — a big import fills in over a few of them — and stopping by itself once nothing is pending.
   *
   * <p>A read of its own rather than a poll of the one above: what changes between two polls is a
   * handful of coordinates, and re-reading the stage for them would put the mandate's every company
   * and full profile back on the wire every three seconds.
   */
  const geocodingPending = talentMap.data?.geocodingPending ?? 0;
  const talentMapLocations = useQuery({
    queryKey: talentMapApi.TALENT_MAP_LOCATIONS_KEY(project.id, stage.status),
    queryFn: ({ signal }) => talentMapApi.getTalentMapLocations(project.id, stage.status, signal),
    enabled: view === "map" && geocodingPending > 0,
    refetchInterval: GEOCODING_POLL_MS,
  });

  // The poll answers the map's own read, so it lands there rather than beside it: one page, however
  // many reads filled it in.
  const polledLocations = talentMapLocations.data;
  useEffect(() => {
    if (!polledLocations) return;
    queryClient.setQueryData(
      talentMapApi.TALENT_MAP_KEY(project.id, stage.status),
      (held: talentMapTypes.TalentMapPage | undefined) =>
        held ? { ...held, ...polledLocations } : held,
    );
  }, [polledLocations, queryClient, project.id, stage.status]);

  const rows = useMemo(() => {
    // The server's Executive-name and Status filters are both company-level (EXISTS: does this
    // company have a matching executive at all), so a page can carry a company for one matching
    // person among several. The grid's own rows are people, not companies, so each ticked/typed
    // filter also narrows which of a kept company's executives get a line — otherwise searching
    // "Alok" would still draw an unrelated colleague's row beneath theirs.
    const normalisedQuery = executiveName.toLowerCase();
    const matchesExecutiveFilters = (candidate: Candidate) => {
      if (executiveStatuses.length > 0 && !executiveStatuses.includes(candidate.status)) return false;
      if (normalisedQuery && !candidate.fullName.toLowerCase().includes(normalisedQuery)) return false;
      return true;
    };
    const people = (mappedPage?.candidates ?? []).filter(matchesExecutiveFilters);
    const unmapped = (unmappedPage?.candidates ?? []).filter(matchesExecutiveFilters);
    return toTriageRows(companies.data?.companies ?? [], people, unmapped);
  }, [companies.data, mappedPage, unmappedPage, executiveStatuses, executiveName]);

  /**
   * What the two people reads could not fit. Both are capped by the server, and a mapping that ran
   * past the cap would otherwise render fewer lines per company with nothing saying so — a talent map
   * that looks complete and is not, on the screen whose whole job is showing what has been mapped.
   *
   * <p>Stated rather than hidden, for the same reason `totalLabel` below refuses to print a count it
   * has not read yet.
   */
  const unlisted = [
    peopleNotShown(mappedPage, "at these companies"),
    peopleNotShown(unmappedPage, "with no company in this mandate"),
  ].filter((line): line is string => line !== null);

  const move = useMutation({
    mutationFn: ({ company, status }: { company: TriageCompany; status: TriageCompanyStatus }) =>
      triageApi.updateTriageCompany(project.id, company.id, { status }),
    onSuccess: (_result, { company, status }) => {
      refreshEveryStage();
      toast(`${company.companyName} moved to ${MOVE_LABELS[status]}`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: (_data, _error, { company }) => clearBusy(company.id),
  });

  const remove = useMutation({
    mutationFn: (company: TriageCompany) => triageApi.deleteTriageCompany(project.id, company.id),
    onSuccess: (_result, company) => {
      refreshEverything();
      setPendingRemoval(null);
      toast(`${company.companyName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: (_data, _error, company) => clearBusy(company.id),
  });

  const exportCsv = useMutation({
    // The debounced terms, not the keystrokes: the file is what the grid is showing, and for 300ms
    // after a keypress those are two different things.
    mutationFn: () => exportApi.saveCompaniesCsv(project.id, stage.status,
      { query: debouncedQuery, executiveQuery: debouncedExecutiveQuery, executiveStatuses },
      [project.clientName, project.positionTitle, stage.label]),
    onError: (error) => toast(messageFor(error)),
  });

  const removeCandidate = useMutation({
    mutationFn: (candidate: Candidate) =>
      candidatesApi.deleteCandidate(project.id, candidate.id),
    onSuccess: (_result, candidate) => {
      refreshEverything();
      setPendingCandidateRemoval(null);
      setProfile(null);
      toast(`${candidate.fullName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
  });

  // Loosened past `TriageCompany`: the add-executive panel holds only its company context, which is
  // exactly the two fields this write and its toast need.
  const markNoExecutiveFound = useMutation({
    mutationFn: (company: { id: string; companyName: string }) =>
      triageApi.updateTriageCompany(project.id, company.id, { noExecutiveFound: true }),
    onSuccess: (_result, company) => {
      refreshEveryStage();
      toast(`${company.companyName}: marked no executive found`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: (_data, _error, company) => clearBusy(company.id),
  });

  const saveNote = useSaveCompanyNote(project.id, refreshEveryStage);
  const changeCandidateStatus = useChangeCandidateStatus(project.id, refreshPeople);

  // A page that outlives its rows — the last company on page 3 was moved away — would otherwise sit
  // on an empty grid with no way back but the pager.
  useEffect(() => {
    if (page > lastPage) setPage(lastPage);
  }, [page, lastPage]);

  return (
    /* No negative margins and no viewport arithmetic: the shell gives this tab the whole main area
       and a definite height (FULL_BLEED_TABS in ProjectLayout), so the height is inherited rather
       than guessed from a hard-coded amount of chrome that any topbar change would falsify. */
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <TriageToolbar
        query={query}
        onQuery={setQuery}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        customColumns={customColumns}
        onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
        onAddCompany={() => setOpenCompany({ company: null })}
        onAddExecutive={() => setProfile({ candidate: null, company: null })}
        onImport={() => setImporting(true)}
        onExport={() => exportCsv.mutate()}
        exporting={exportCsv.isPending}
        onManageColumns={() => setManagingColumns(true)}
        canWrite={canWrite}
        canImport={stage.status === "inUniverse"}
        view={view}
        onViewChange={mapOffered ? (next) => setMapPreferences({ view: next }) : undefined}
      />

      <ImportSpreadsheetDialog
        open={importing}
        projectId={project.id}
        customColumns={customColumns}
        onClose={() => setImporting(false)}
        onImported={refreshEverything}
      />

      <ManageColumnsDialog
        open={managingColumns}
        projectId={project.id}
        columns={customColumns}
        initialRenameId={editColumnId ?? undefined}
        onClose={() => {
          setManagingColumns(false);
          setEditColumnId(null);
        }}
      />

      {view === "map" ? (
        <TalentMapView
          projectId={project.id}
          page={talentMap.data}
          query={query}
          accessToken={mapConfig.data?.publicToken ?? ""}
          canWrite={canWrite}
          loading={talentMap.isFetching}
          error={talentMap.isError}
          preferences={mapPreferences}
          onPreferences={setMapPreferences}
          onOpenCompany={(company) => setOpenCompany({ company })}
          onOpenCandidate={(candidate) => setProfile({ candidate, company: null })}
          onAddExecutive={(company) =>
            setProfile({
              candidate: null,
              company: { triageCompanyId: company.id, companyName: company.companyName },
            })
          }
        />
      ) : (
      /* `min-h-0`: a `flex-1` child of a flex *column* keeps `min-height: auto` and refuses to
         shrink, so without it the grid grows to the height of every row it holds and the whole
         screen scrolls — header and pager included — rather than the rows scrolling under them. */
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-3 p-3 sm:p-5">
        <TriageCompanyTable
          rows={rows}
          projectId={project.id}
          label={`${stage.label} companies`}
          sort={sort}
          onSortChange={setSort}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          customColumns={customColumns}
          layout={layout}
          onLayoutChange={setLayout}
          loading={companies.isFetching}
          error={companies.isError}
          emptyMessage={
            debouncedQuery || debouncedExecutiveQuery || executiveStatuses.length > 0
              ? "No companies match that search."
              : stage.emptyMessage
          }
          columnFilters={{
            name: {
              value: query,
              onChange: setQuery,
              "aria-label": "Filter by company name",
            },
            executive: {
              value: executiveQuery,
              onChange: setExecutiveQuery,
              "aria-label": "Filter by executive name",
            },
            ...(executiveStatusOptions.length > 1
              ? {
                  executiveStatus: {
                    kind: "check" as const,
                    options: executiveStatusOptions,
                    selected: executiveStatuses,
                    onChange: setExecutiveStatuses,
                    "aria-label": "Filter by status",
                  },
                }
              : {}),
          }}
          onEditColumn={(customColumnId) => {
            setEditColumnId(customColumnId);
            setManagingColumns(true);
          }}
          onMove={(company, status) => {
            markBusy(company.id);
            move.mutate({ company, status });
          }}
          onDelete={setPendingRemoval}
          onAddExecutive={(company) =>
            setProfile({
              candidate: null,
              company: { triageCompanyId: company.id, companyName: company.companyName },
            })
          }
          onSaveNote={(company, note) => saveNote.mutateAsync({ company, note })}
          onChangeCandidateStatus={(candidate, status) => {
            markBusy(candidate.id);
            changeCandidateStatus.mutate(
              { candidateId: candidate.id, status },
              { onSettled: () => clearBusy(candidate.id) },
            );
          }}
          onEditCandidate={(candidate) => setProfile({ candidate, company: null })}
          onRemoveCandidate={setPendingCandidateRemoval}
          onOpenCompany={(company) => setOpenCompany({ company })}
          busyIds={busyIds}
          canWrite={canWrite}
        />

        {unlisted.map((line) => (
          <p key={line} role="status" className="flex-none font-mono text-[11.5px] text-u-text3">
            {line}
          </p>
        ))}

        <PaginationBar
          page={page}
          size={pageSize}
          totalCount={totalCount}
          onPage={setPage}
          onSize={setPageSize}
          trailing={<FullscreenButton active={isFullscreen} onToggle={toggleFullscreen} />}
        />
      </div>
      )}

      <CompanyDrawer
        open={openCompany !== null}
        projectId={project.id}
        company={openCompany?.company ?? null}
        landingStatus={stage.status}
        customColumns={companyColumns}
        canWrite={canWrite}
        onClose={() => setOpenCompany(null)}
        onSaved={refreshEverything}
        onMove={(company, status) => {
          markBusy(company.id);
          setOpenCompany(null);
          move.mutate({ company, status });
        }}
        onDelete={(company) => {
          setOpenCompany(null);
          setPendingRemoval(company);
        }}
        // One panel at a time: the company's closes as the new executive's opens on it.
        onAddExecutive={(company) => {
          setOpenCompany(null);
          setProfile({
            candidate: null,
            company: { triageCompanyId: company.id, companyName: company.companyName },
          });
        }}
        onMarkNoExecutiveFound={(company) => {
          markBusy(company.id);
          markNoExecutiveFound.mutate(company);
        }}
        markingNoExecutiveFound={!!openCompany?.company && busyIds.has(openCompany.company.id)}
      />

      <CandidateDrawer
        open={profile !== null}
        projectId={project.id}
        candidate={profile?.candidate ?? null}
        company={profile?.company ?? null}
        customColumns={candidateColumns}
        canWrite={canWrite}
        defaultCurrency={briefCompensation.data?.currency}
        onClose={() => setProfile(null)}
        // The panel stays open on what the server answered: a corrected figure shows corrected
        // before the grid has refetched, and an add moves straight on to the profile it made.
        onSaved={(saved) => {
          setProfile({ candidate: saved, company: null });
          refreshEverything();
        }}
        onDelete={canWrite ? setPendingCandidateRemoval : undefined}
        // The add form's other conclusion: the research happened and nobody fit, so the panel closes
        // on the flag rather than on a saved profile.
        onMarkNoExecutiveFound={
          canWrite && profileCompany
            ? () => {
                markBusy(profileCompany.triageCompanyId);
                markNoExecutiveFound.mutate({
                  id: profileCompany.triageCompanyId,
                  companyName: profileCompany.companyName,
                });
                setProfile(null);
              }
            : undefined
        }
      />

      <RemoveCompanyDialog
        company={pendingRemoval}
        removing={remove.isPending}
        onCancel={() => setPendingRemoval(null)}
        onConfirm={(company) => {
          markBusy(company.id);
          remove.mutate(company);
        }}
      />

      <RemoveCandidateDialog
        candidate={pendingCandidateRemoval}
        removing={removeCandidate.isPending}
        onCancel={() => setPendingCandidateRemoval(null)}
        onConfirm={(candidate) => removeCandidate.mutate(candidate)}
      />
    </div>
  );
}

/**
 * One line naming what a capped read left out, or null when it left nothing out. `totalCount` is the
 * server's count of everything matching, so the gap between it and what arrived is exactly the number
 * of people this page cannot show.
 */
function peopleNotShown(page: CandidatesPage | undefined, where: string): string | null {
  if (!page || page.totalCount <= page.candidates.length) return null;
  return `Showing ${page.candidates.length} of ${page.totalCount} executives ${where}.`;
}

/**
 * What the profile drawer is open on: an existing executive, or a blank one at a known company. Both
 * null is the toolbar's "Add executive", which maps someone with no company at all.
 */
interface OpenProfile {
  candidate: Candidate | null;
  company: CandidateCompanyContext | null;
}

/**
 * What the company panel is open on. A wrapper rather than a bare `TriageCompany | null`, because
 * "closed" and "open on a new company" are both null on their own and the panel has to tell them
 * apart — one shows nothing, the other shows the Add form.
 */
interface OpenCompany {
  company: TriageCompany | null;
}
