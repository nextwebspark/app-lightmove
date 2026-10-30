import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState, type ComponentProps } from "react";
import { Navigate, useLocation, useNavigate, useOutletContext, useParams, useSearchParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { ICONS } from "../../../components/layout/Icon";
import { FullscreenButton } from "../../../components/ui";
import { ColumnPickerList, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { SelectionAction, SelectionActionBar } from "../../../components/ui/SelectionActionBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { DEFAULT_PAGE_SIZE } from "../../../lib/paging";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { sortableFieldsOf, useGridSort, type GridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate } from "../../candidates/api/types";
import {
  CandidateDrawer,
  type CandidateCompanyContext,
} from "../../candidates/components/CandidateDrawer";
import { RemoveCandidateDialog } from "../../candidates/components/RemoveCandidateDialog";
import { useChangeCandidateStatus } from "../../candidates/lib/useChangeCandidateStatus";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import { useDeleteCustomColumn } from "../../customcolumns/lib/useDeleteCustomColumn";
import type { CustomColumn } from "../../customcolumns/api/types";
import * as positionApi from "../../position/api/positionApi";
import { EnrolDialog } from "../../outreach/components/EnrolDialog";
import { refreshOutreachActivity } from "../../outreach/lib/outreachRefresh";
import { useMailbox } from "../../outreach/lib/useMailbox";
import { canExecuteProjectWork } from "../../projects/lib/access";
import { isArchived } from "../../projects/lib/filtering";
import { PipelineView } from "../../pipeline/components/PipelineView";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import type * as talentMapTypes from "../../talentmap/api/types";
import { TalentMapView } from "../../talentmap/components/TalentMapView";
import { useTalentMapPreferences, type CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as exportApi from "../api/exportApi";
import * as sourcingApi from "../api/sourcingApi";
import * as triageApi from "../api/triageApi";
import { executiveStatusOptions as statusFilterOptions, statusKeysOf } from "../lib/executiveStatusFilter";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import type { AdvancedMenuSection } from "../components/AdvancedMenu";
import { CompanyDrawer, type CompanyDrawerTab } from "../components/CompanyDrawer";
import { ExecutiveSourcingBanner } from "../components/ExecutiveSourcingBanner";
import { FindExecutivesDialog } from "../components/FindExecutivesDialog";
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
import { awaitingResearch, type TriageCompanyRow } from "../lib/triageRows";
import { stageBySlug, TRIAGE_STAGES } from "../lib/triageStages";
import { companiesOf } from "../lib/sourcingSummary";
import { CHOSEN_VIEW_STATE, isChosenView, resolveCompaniesView, useCompaniesViews } from "../lib/useCompaniesViews";
import { useCompanySelection } from "../lib/useCompanySelection";
import { useExecutiveSourcing } from "../lib/useExecutiveSourcing";
import { useProjectStream, type ProjectStreamKind } from "../../../lib/useProjectStream";

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

/** The server reads at most this many ticked companies' executives for one Add to sequence. */
const ADD_TO_SEQUENCE_COMPANY_CAP = 200;

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
 * One stage of a mandate's companies — In universe, Shortlisted or Declined — as its own page. In universe reads as a
 * Table, a Map or its executives' Pipeline, one search box narrowing whichever is open.
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
  /** The toolbar's search, the same in every view: the server matches it, so the three views agree. */
  const [search, setSearch] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  /** The Company column's own header filter: the company's name alone. */
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  /** The Executive column's own header filter — independent of the Company one above it. */
  const [executiveQuery, setExecutiveQuery] = useState("");
  const [debouncedExecutiveQuery, setDebouncedExecutiveQuery] = useState("");
  /**
   * The Status column's own header filter — a closed checkbox set, applied with no debounce. A ticked
   * stage stands for every status under it, which is what the server is sent.
   */
  const [executiveStatusChoice, setExecutiveStatusChoice] = useState<string[]>([]);
  const { catalog: pipelineCatalog } = usePipelineCatalog();
  const executiveStatuses = useMemo(
    () => statusKeysOf(executiveStatusChoice, pipelineCatalog),
    [executiveStatusChoice, pipelineCatalog],
  );
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
  const companySelection = useCompanySelection();
  const [confirmingFindExecutives, setConfirmingFindExecutives] = useState(false);
  const [enrolment, setEnrolment] = useState<Enrolment | null>(null);
  const [pipelineSelection, setPipelineSelection] = useState<ReadonlySet<string>>(() => new Set());
  const [inactiveOpen, setInactiveOpen] = useState(true);
  const [managingColumns, setManagingColumns] = useState(false);
  /** Set when "Edit field" is opened from a header menu, so the dialog lands already renaming it. */
  const [editColumnId, setEditColumnId] = useState<string | null>(null);

  // The phone's quick actions land here as `?add=company|executive`: the toolbar's own Add, pressed for them.
  const [searchParams, setSearchParams] = useSearchParams();
  const addRequest = searchParams.get("add");
  useEffect(() => {
    if (addRequest !== "company" && addRequest !== "executive") return;
    // Who may write is read off the signed-in user, so the request waits for them rather than being dropped.
    if (!user) return;
    if (canWrite) {
      if (addRequest === "company") setOpenCompany({ company: null });
      else setProfile({ candidate: null, company: null });
    }
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("add");
        return next;
      },
      { replace: true },
    );
  }, [addRequest, user, canWrite, setSearchParams]);
  const [sort, setSort] = useGridSort<TriageSortField>("companies", project.id, TRIAGE_SORT_FIELDS, DEFAULT_SORT);
  const [isFullscreen, toggleFullscreen] = useFullscreen();
  const [mapPreferences, setMapPreferences] = useTalentMapPreferences(project.id);
  const { views, mapConfig } = useCompaniesViews(stage.status, canWrite);
  const requestedView = searchParams.get("view");
  const view = resolveCompaniesView(views, requestedView, mapPreferences.view);
  // A chosen view is remembered once the page shows it, never while it is only the fallback of a map config still
  // loading — and only on In universe, the one stage with views to remember.
  const { state: locationState } = useLocation();
  const viewChosen = isChosenView(locationState) && views.length > 1;
  useEffect(() => {
    if (viewChosen && requestedView === view && mapPreferences.view !== view) setMapPreferences({ view });
  }, [viewChosen, requestedView, view, mapPreferences.view, setMapPreferences]);
  useEffect(
    () => {
      companySelection.clear();
      setPipelineSelection(new Set());
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [view],
  );
  const navigate = useNavigate();
  const handleViewChange = (next: CompaniesView) => {
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current);
        params.set("view", next);
        params.delete("candidate");
        params.delete("note");
        params.delete("section");
        params.delete("tab");
        return params;
      },
      { replace: true, state: CHOSEN_VIEW_STATE },
    );
  };

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
  const hideableColumns = useMemo(
    () => hideableColumnsOf(createTriageCompanyColumns(customColumns, { staff: canWrite })),
    [customColumns, canWrite],
  );
  const hiddenColumnCount = hideableColumns.filter((column) => columnVisibility[column.id] === false).length;

  // A sort on a column this reader no longer has — a custom column removed, a staff column on a client seat — is a
  // 400 from the server, so the grid falls back to its default rather than failing.
  const sortableFields = useMemo(
    () => sortableFieldsOf(createTriageCompanyColumns(customColumns, { staff: canWrite })),
    [customColumns, canWrite],
  );
  useEffect(() => {
    if (!customColumnsQuery.isSuccess || sortableFields.includes(sort.field)) return;
    setSort(DEFAULT_SORT);
  }, [customColumnsQuery.isSuccess, sortableFields, sort.field, setSort]);

  // A keystroke should narrow the list, not fire a request per character.
  useEffect(() => {
    // Trimmed once here, so a trailing space neither refetches the same page under a new key nor reads as a search.
    const timer = setTimeout(() => setDebouncedSearch(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

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
    () => {
      setPage(0);
      // A tick made on one reading of the stage must not ride silently into another.
      companySelection.clear();
      setPipelineSelection(new Set());
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [debouncedSearch, debouncedQuery, debouncedExecutiveQuery, executiveStatuses.join(","), sort],
  );

  /**
   * Every write invalidates the whole prefix rather than this stage's key. A move changes two stages
   * and all three counts, and a page that refreshed only the list it was looking at would show the
   * company gone and the sidebar's shortlist badge still one short.
   */
  const refreshEveryStage = () => {
    void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });
    void queryClient.invalidateQueries({ queryKey: triageApi.STAGE_LINES_KEY_PREFIX(project.id) });
  };

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
    // A move changes which companies a stage lists and its badges, not only its people.
    if (kinds.some((kind) => kind.startsWith("company-") || kind === "candidate-moved")) {
      refreshEveryStage();
    }
    if (kinds.some((kind) => kind.startsWith("candidate-"))) {
      refreshPeople();
    }
    if (kinds.includes("executive-sourcing")) {
      void queryClient.invalidateQueries({ queryKey: sourcingApi.SOURCING_LATEST_KEY(project.id) });
    }
    // An executive's drawer opened here carries their Outreach fold and next action.
    if (kinds.includes("outreach") || kinds.includes("task")) {
      refreshOutreachActivity(queryClient, project.id);
    }
    if (kinds.includes("task")) {
      void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
    }
    // Both halves are points on the globe. Free while the grid is the view: the map's reads are
    // disabled there, and an inactive query is marked stale rather than refetched. Outreach and tasks
    // move neither half.
    if (kinds.some((kind) => kind !== "outreach" && kind !== "task")) {
      refreshMap();
    }
  };

  const streamIsLive = useProjectStream(project.id, refreshWhatMoved);

  /** Offered on the universe only, and only where the deployment has a people search. */
  const sourcing = useExecutiveSourcing(
    project.id,
    canWrite && stage.status === "inUniverse",
    streamIsLive,
    companySelection.deselect,
  );
  const findExecutivesOffered = canWrite && stage.status === "inUniverse" && sourcing.offered;
  const findExecutivesCap = sourcing.config?.maxCompaniesPerRun ?? 0;
  const mailbox = useMailbox(canWrite);
  /** Ticked companies' or executives' people go to Add to sequence; never from Declined. */
  const addToSequenceOffered =
    canWrite &&
    stage.status !== "declined" &&
    mailbox.data?.offered === true &&
    !isArchived(project);
  const selectionOffered = view === "table" && (findExecutivesOffered || addToSequenceOffered);
  const selectionCap = addToSequenceOffered ? Math.max(findExecutivesCap, ADD_TO_SEQUENCE_COMPANY_CAP) : findExecutivesCap;
  const selectionAction = addToSequenceOffered ? "Add to sequence" : "Find executives";

  const handleFindExecutives = () => {
    if (companySelection.selectedIds.size > findExecutivesCap) {
      toast(`Find executives takes ${companiesOf(findExecutivesCap)} at a time`);
      return;
    }
    setConfirmingFindExecutives(true);
  };

  const handleStartFindExecutives = () =>
    sourcing.start([...companySelection.selectedIds], () => setConfirmingFindExecutives(false));

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

  /**
   * The grid's lines — people at companies, a company nobody is listed at yet, an executive at no company of the
   * mandate — ordered by the server across the whole stage, so sorting by a person's name or title sorts the people
   * whatever company they sit at. Filtered per person there too, so a company kept for one matching colleague draws
   * only that colleague.
   */
  const linesScope: triageApi.StageLinesQuery = {
    status: stage.status,
    page,
    size: pageSize,
    query: debouncedQuery,
    executiveQuery: debouncedExecutiveQuery.trim(),
    executiveStatuses,
    search: debouncedSearch,
    sort,
  };
  const lines = useQuery({
    queryKey: triageApi.STAGE_LINES_KEY(project.id, linesScope),
    queryFn: ({ signal }) => triageApi.getStageLines(project.id, linesScope, signal),
    // The grid's reads are the grid's: the map reads the whole stage in one request of its own.
    enabled: view === "table",
    // Paging without blanking the grid, which would make every page turn look like a reload.
    placeholderData: keepPreviousData,
    refetchInterval: researchPoll,
  });

  const totalCount = lines.data?.totalCount;
  const lastPage = Math.max(0, Math.ceil((totalCount ?? 0) / pageSize) - 1);

  visiblePeople.current = (lines.data?.lines ?? [])
    .map((line) => line.candidate)
    .filter((candidate): candidate is Candidate => candidate !== null);

  /**
   * Which Status values the Status column's header menu offers — only the ones actually borne by an
   * executive the mandate has mapped, so a mandate with just Identified and Contacted people never
   * sees the other five sitting there unusable. Learned only from an unfiltered read (the component
   * remounts fresh per project and stage, so the first page is always one): once either header filter
   * narrows what loads, that narrower set must not overwrite the true one the checkboxes describe.
   */
  const [seenExecutiveStatuses, setSeenExecutiveStatuses] = useState<Set<string>>(
    () => new Set(),
  );
  useEffect(() => {
    if (executiveStatuses.length > 0 || debouncedExecutiveQuery.trim() || debouncedSearch || visiblePeople.current.length === 0) return;
    setSeenExecutiveStatuses((current) => {
      const next = new Set(current);
      for (const candidate of visiblePeople.current) next.add(candidate.status);
      return next.size === current.size ? current : next;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lines.data, executiveStatuses.length, debouncedExecutiveQuery, debouncedSearch]);
  const executiveStatusOptions = useMemo(
    () => statusFilterOptions(pipelineCatalog, seenExecutiveStatuses),
    [pipelineCatalog, seenExecutiveStatuses],
  );

  /** The whole stage as points, read once when the globe opens and again when the mandate changes. */
  const talentMap = useQuery({
    queryKey: talentMapApi.TALENT_MAP_KEY(project.id, stage.status, debouncedSearch),
    queryFn: ({ signal }) => talentMapApi.getTalentMap(project.id, stage.status, debouncedSearch, signal),
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
    queryKey: talentMapApi.TALENT_MAP_LOCATIONS_KEY(project.id, stage.status, debouncedSearch),
    queryFn: ({ signal }) => talentMapApi.getTalentMapLocations(project.id, stage.status, debouncedSearch, signal),
    enabled: view === "map" && geocodingPending > 0,
    refetchInterval: GEOCODING_POLL_MS,
  });

  // The poll answers the map's own read, so it lands there rather than beside it: one page, however
  // many reads filled it in.
  const polledLocations = talentMapLocations.data;
  useEffect(() => {
    if (!polledLocations) return;
    queryClient.setQueryData(
      talentMapApi.TALENT_MAP_KEY(project.id, stage.status, debouncedSearch),
      (held: talentMapTypes.TalentMapPage | undefined) =>
        held ? { ...held, ...polledLocations } : held,
    );
  }, [polledLocations, queryClient, project.id, stage.status, debouncedSearch]);

  const rows = useMemo(() => {
    const addedBy = lines.data?.addedBy ?? {};
    return (lines.data?.lines ?? []).flatMap((line): TriageCompanyRow[] => {
      const candidateAddedBy = line.candidate ? addedBy[line.candidate.id] : undefined;
      if (line.company) return [{ company: line.company, candidate: line.candidate, candidateAddedBy }];
      return line.candidate ? [{ company: null, candidate: line.candidate, candidateAddedBy }] : [];
    });
  }, [lines.data]);

  /**
   * A company's move carries the people still where it stood, and someone moved on their own stays put (#903). The
   * page reads who those are first and names them, so the toast can count them and Undo brings back exactly them.
   */
  const move = useMutation({
    mutationFn: async ({ company, status, executiveIds }: CompanyMove) => {
      const carried = executiveIds ?? (await executivesStandingWith(company));
      await triageApi.updateTriageCompany(project.id, company.id, { status, executiveIds: carried });
      return carried;
    },
    onSuccess: (carried, { company, status, isUndo }) => {
      void rowsChanged(project.id);
      const withThem = carried && carried.length > 0 ? ` and ${executivesOf(carried.length)}` : "";
      const message = `${company.companyName}${withThem} moved to ${MOVE_LABELS[status]}`;
      if (isUndo) {
        toast(message);
        return;
      }
      toast.success(message, {
        undo: () => {
          markBusy(company.id);
          move.mutate({
            company: { ...company, status },
            status: company.status,
            executiveIds: carried,
            isUndo: true,
          });
        },
      });
    },
    onError: (error) => toast.error(messageFor(error)),
    onSettled: (_data, _error, { company }) => clearBusy(company.id),
  });

  /** Undefined when the read could not fit them all: the server then carries everyone at the old stage itself. */
  const executivesStandingWith = async (company: TriageCompany): Promise<string[] | undefined> => {
    const standing = await candidatesApi.getCandidates(project.id, {
      triageCompanyIds: [company.id],
      triageStatus: company.status,
    });
    return standing.totalCount > standing.candidates.length
      ? undefined
      : standing.candidates.map((candidate) => candidate.id);
  };

  /** Who a removal leaves on the position with no company, read only while its dialog is open. */
  const removalScope = { triageCompanyIds: pendingRemoval ? [pendingRemoval.id] : [] };
  const peopleAtRemoval = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(project.id, removalScope),
    queryFn: ({ signal }) => candidatesApi.getCandidates(project.id, removalScope, signal),
    enabled: pendingRemoval !== null,
  });

  /** One executive alone (#903): their company, and everyone else at it, stay where they stand. */
  const moveCandidate = useMutation({
    mutationFn: ({ candidate, status }: { candidate: Candidate; status: TriageCompanyStatus; isUndo?: boolean }) =>
      candidatesApi.moveCandidateToStage(project.id, candidate.id, status),
    onSuccess: (_result, { candidate, status, isUndo }) => {
      void rowsChanged(project.id);
      const message = `${candidate.fullName} moved to ${MOVE_LABELS[status]}`;
      if (isUndo) {
        toast(message);
        return;
      }
      const previous = candidate.triageStatus ?? stage.status;
      toast.success(message, {
        undo: () => {
          markBusy(candidate.id);
          moveCandidate.mutate({ candidate: { ...candidate, triageStatus: status }, status: previous, isUndo: true });
        },
      });
    },
    onError: (error) => toast.error(messageFor(error)),
    onSettled: (_data, _error, { candidate }) => clearBusy(candidate.id),
  });

  const remove = useMutation({
    mutationFn: (company: TriageCompany) => triageApi.deleteTriageCompany(project.id, company.id),
    onSuccess: (_result, company) => {
      refreshEverything();
      setPendingRemoval(null);
      toast(`${company.companyName} removed from this mandate`);
    },
    onError: (error) => toast.error(messageFor(error)),
    onSettled: (_data, _error, company) => clearBusy(company.id),
  });

  const exportCsv = useMutation({
    // The debounced terms, not the keystrokes: the file is what the grid is showing, and for 300ms
    // after a keypress those are two different things.
    mutationFn: () => exportApi.saveCompaniesCsv(project.id, stage.status,
      { search: debouncedSearch, query: debouncedQuery, executiveQuery: debouncedExecutiveQuery, executiveStatuses },
      [project.clientName, project.positionTitle, stage.label]),
    onError: (error) => toast.error(messageFor(error)),
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
    onError: (error) => toast.error(messageFor(error)),
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
    onError: (error) => toast.error(messageFor(error)),
    onSettled: (_data, _error, company) => clearBusy(company.id),
  });

  const changeCandidateStatus = useChangeCandidateStatus(project.id, refreshPeople);

  const deleteColumn = useDeleteCustomColumn(project.id);

  const togglePipelineSelected = (candidateId: string) =>
    setPipelineSelection((current) => {
      const next = new Set(current);
      if (next.has(candidateId)) next.delete(candidateId);
      else next.add(candidateId);
      return next;
    });

  const enrolTicked = () => {
    if (view === "pipeline") {
      const ids = [...pipelineSelection];
      setEnrolment({
        scope: { candidateIds: ids },
        source: `From the ${ids.length === 1 ? "executive" : `${ids.length} executives`} you ticked on the pipeline`,
      });
      return;
    }
    const ids = [...companySelection.selectedIds];
    setEnrolment({
      scope: { triageCompanyIds: ids, triageStatus: stage.status },
      source: `From the ${companiesOf(ids.length)} you ticked on ${stage.label}`,
    });
  };

  /** What Advanced ▾ holds, per view (`Position.dc.html`'s `advancedItems`): only what that view can use. */
  const advancedSections = (): AdvancedMenuSection[] => {
    const sections: AdvancedMenuSection[] = [];
    if (view === "table") {
      sections.push({
        label: "Layout",
        items: [
          {
            key: "columns",
            label: "Show / hide columns",
            icon: ICONS.columns,
            badge: hiddenColumnCount > 0 ? `${hiddenColumnCount} hidden` : undefined,
            panel: () => (
              <ColumnPickerList
                columns={hideableColumns}
                visibility={columnVisibility}
                defaults={defaultVisibility}
                onChange={setColumnVisibility}
                onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
              />
            ),
          },
          ...(canWrite
            ? [
                {
                  key: "manageColumns",
                  label: "Manage custom columns…",
                  icon: ICONS.settings,
                  hint: "This position's own fields",
                  onSelect: () => setManagingColumns(true),
                },
              ]
            : []),
        ],
      });
    } else if (view === "map") {
      sections.push({
        label: "Map",
        items: [
          {
            key: "mapExecutives",
            label: "Executives on the map",
            icon: ICONS.candidates,
            checked: mapPreferences.showExecutives,
            onSelect: () => setMapPreferences({ showExecutives: !mapPreferences.showExecutives }),
          },
          {
            key: "mapPanel",
            label: "Mapping panel",
            icon: ICONS.table,
            checked: !mapPreferences.panelCollapsed,
            onSelect: () => setMapPreferences({ panelCollapsed: !mapPreferences.panelCollapsed }),
          },
        ],
      });
    } else {
      sections.push({
        label: "Board",
        items: [
          {
            key: "inactive",
            label: "Inactive column",
            icon: ICONS.eyeOff,
            hint: "Executives at an out-of-process status",
            checked: inactiveOpen,
            onSelect: () => setInactiveOpen((open) => !open),
          },
          {
            key: "stages",
            label: "Edit pipeline stages…",
            icon: ICONS.settings,
            hint: "Workspace settings",
            onSelect: () => navigate("/settings/pipeline-stages"),
          },
        ],
      });
    }
    if (addToSequenceOffered && view !== "map") {
      const ticked = view === "pipeline" ? pipelineSelection.size : companySelection.selectedIds.size;
      sections.push({
        label: "Outreach",
        items: [
          {
            key: "sequence",
            label: "Add to sequence…",
            icon: ICONS.outreach,
            disabled: ticked === 0,
            hint:
              ticked > 0
                ? `${ticked} ticked`
                : view === "pipeline"
                  ? "Tick executives on the board first"
                  : "Tick companies first",
            onSelect: enrolTicked,
          },
        ],
      });
    }
    sections.push({
      label: "Data",
      items: [
        ...(canWrite && stage.status === "inUniverse"
          ? [
              {
                key: "import",
                label: "Import spreadsheet…",
                icon: ICONS.importInto,
                hint: "CSV or Excel · companies and people",
                onSelect: () => setImporting(true),
              },
            ]
          : []),
        {
          key: "export",
          label: exportCsv.isPending ? "Exporting…" : "Export CSV",
          icon: ICONS.exportOut,
          disabled: exportCsv.isPending,
          hint: debouncedSearch.trim() ? "This stage, as searched" : "Every row of this stage",
          onSelect: () => exportCsv.mutate(),
        },
      ],
    });
    return sections;
  };

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
        search={search}
        onSearch={setSearch}
        searchPlaceholder={
          !canWrite
            ? "Search companies and executives…"
            : view === "pipeline"
              ? "Search executives, companies, notes, tags…"
              : "Search companies, executives, notes, tags…"
        }
        views={views}
        view={view}
        onViewChange={handleViewChange}
        isFullscreen={isFullscreen}
        canWrite={canWrite}
        onAddCompany={() => setOpenCompany({ company: null })}
        addCompanyUnavailable={view === "pipeline" ? { reason: "Add companies from Table or Map" } : undefined}
        onAddExecutive={() => setProfile({ candidate: null, company: null })}
        findExecutives={
          findExecutivesOffered
            ? {
                onPress: handleFindExecutives,
                running: sourcing.isRunning,
                progress: sourcing.run && sourcing.isRunning
                  ? `${sourcing.run.companiesDone}/${sourcing.run.companiesTotal}`
                  : undefined,
                unavailable:
                  view === "pipeline" ? { reason: "Find executives from Table, where companies are ticked" } : undefined,
              }
            : undefined
        }
        advanced={advancedSections()}
        advancedDot={
          view === "table" && hiddenColumnCount > 0
            ? `${hiddenColumnCount} column${hiddenColumnCount === 1 ? "" : "s"} hidden`
            : undefined
        }
      />

      {findExecutivesOffered && sourcing.run && !sourcing.isDismissed && (
        <ExecutiveSourcingBanner run={sourcing.run} onDismiss={sourcing.dismiss} />
      )}

      {sourcing.config && (
        <FindExecutivesDialog
          open={confirmingFindExecutives}
          config={sourcing.config}
          selectedCount={companySelection.selectedIds.size}
          starting={sourcing.isStarting}
          onCancel={() => setConfirmingFindExecutives(false)}
          onConfirm={handleStartFindExecutives}
        />
      )}

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

      {view === "pipeline" ? (
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          <PipelineView
            project={project}
            search={debouncedSearch}
            selectedIds={pipelineSelection}
            onToggleSelected={togglePipelineSelected}
            inactiveOpen={inactiveOpen}
            onInactiveOpenChange={setInactiveOpen}
          />
          {addToSequenceOffered && pipelineSelection.size > 0 && (
            <SelectionActionBar
              count={pipelineSelection.size}
              noun="executive"
              plural="executives"
              onClear={() => setPipelineSelection(new Set())}
            >
              <SelectionAction icon={ICONS.mail} label="Add to sequence" onClick={enrolTicked} />
            </SelectionActionBar>
          )}
        </div>
      ) : view === "map" ? (
        <TalentMapView
          projectId={project.id}
          page={talentMap.data}
          searching={debouncedSearch.trim() !== ""}
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
          loading={lines.isFetching}
          error={lines.isError}
          emptyMessage={
            debouncedSearch || debouncedQuery || debouncedExecutiveQuery || executiveStatuses.length > 0
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
                    selected: executiveStatusChoice,
                    onChange: setExecutiveStatusChoice,
                    "aria-label": "Filter by status",
                  },
                }
              : {}),
          }}
          onEditColumn={(customColumnId) => {
            setEditColumnId(customColumnId);
            setManagingColumns(true);
          }}
          onDeleteColumn={
            canWrite
              ? (customColumnId) => {
                  const column = customColumns.find((entry) => entry.id === customColumnId);
                  if (column) deleteColumn.mutate(column);
                }
              : undefined
          }
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
          onOpenCompanyNotes={(company) => setOpenCompany({ company, tab: "notes" })}
          onChangeCandidateStatus={(candidate, status) => {
            markBusy(candidate.id);
            changeCandidateStatus.mutate(
              { candidateId: candidate.id, status, previous: candidate.status },
              { onSettled: () => clearBusy(candidate.id) },
            );
          }}
          onEditCandidate={(candidate) => setProfile({ candidate, company: null })}
          onRemoveCandidate={setPendingCandidateRemoval}
          onMoveCandidate={(candidate, status) => {
            markBusy(candidate.id);
            moveCandidate.mutate({ candidate, status });
          }}
          onOpenCompany={(company) => setOpenCompany({ company })}
          busyIds={busyIds}
          canWrite={canWrite}
          selection={
            selectionOffered
              ? {
                  selectedIds: companySelection.selectedIds,
                  onToggle: (companyId) => companySelection.toggle(companyId, selectionCap, selectionAction),
                  onToggleAll: (companyIds) =>
                    companySelection.toggleAll(companyIds, selectionCap, selectionAction),
                }
              : undefined
          }
        />

        {selectionOffered && companySelection.selectedIds.size > 0 && !confirmingFindExecutives && (
          <SelectionActionBar
            count={companySelection.selectedIds.size}
            noun="company"
            plural="companies"
            onClear={companySelection.clear}
          >
            {addToSequenceOffered && (
              <SelectionAction
                icon={ICONS.mail}
                label="Add to sequence"
                onClick={enrolTicked}
              />
            )}
            {findExecutivesOffered && (
              <SelectionAction
                icon={ICONS.search}
                label="Find executives"
                disabled={sourcing.isRunning}
                onClick={handleFindExecutives}
              />
            )}
          </SelectionActionBar>
        )}

        {lines.data?.truncated && (
          <p role="status" className="flex-none font-mono text-[11.5px] text-u-text3">
            This stage is larger than one read can sort, so not every company or executive is listed. Search or filter to narrow it.
          </p>
        )}

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

      {enrolment && (
        <EnrolDialog
          projectId={project.id}
          scope={enrolment.scope}
          source={enrolment.source}
          onClose={() => setEnrolment(null)}
        />
      )}

      <CompanyDrawer
        open={openCompany !== null}
        projectId={project.id}
        company={openCompany?.company ?? null}
        initialTab={openCompany?.tab ?? "overview"}
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
        executiveCount={pendingRemoval ? peopleAtRemoval.data?.totalCount : undefined}
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

function executivesOf(count: number): string {
  return count === 1 ? "1 executive" : `${count} executives`;
}

/** Who Add to sequence was asked for: the ticked companies' executives, or the ticked executives. */
interface Enrolment {
  scope: ComponentProps<typeof EnrolDialog>["scope"];
  source: string;
}

interface CompanyMove {
  company: TriageCompany;
  status: TriageCompanyStatus;
  /** Who moves with it; read from the company's old stage when not named. */
  executiveIds?: string[];
  isUndo?: boolean;
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
  /** The panel's tab to open on; its Overview when not said. */
  tab?: CompanyDrawerTab;
}
