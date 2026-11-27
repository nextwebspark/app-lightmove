import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useRef, useState, type ComponentProps } from "react";
import { Navigate, useLocation, useOutletContext, useParams, useSearchParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { ICONS } from "../../../components/layout/Icon";
import { FullscreenButton } from "../../../components/ui";
import { ColumnPickerList, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { GridFilterButton } from "../../../components/ui/GridFilters";
import type { DataGridColumnFilter } from "../../../lib/gridFilters";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useToast } from "../../../components/ui/Toast";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useProjectRowsChanged } from "../../../lib/projectRows";
import { useAddressedPanel } from "../../../lib/useAddressedPanel";
import { useAddressedQuery } from "../../../lib/useAddressedSearch";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { useAddressedGridPaging } from "../../../lib/useGridPaging";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { sortableFieldsOf, useGridSort, type GridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import * as shareConsentApi from "../../candidates/api/shareConsentApi";
import { OPEN_RECORD_COMPANIONS } from "../../candidates/lib/drawerTabs";
import { useConsentMode } from "../../candidates/lib/useConsentMode";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import { useExecutivesAtCompany } from "../../candidates/lib/useExecutivesAtCompany";
import type { Candidate, CandidatesPage } from "../../candidates/api/types";
import { CandidateDrawer } from "../../candidates/components/CandidateDrawer";
import type { RecordStepper } from "../../candidates/components/RecordCornerControls";
import {
  CandidateRecordPage,
  RecordListPager,
  RecordPageEmpty,
  type RecordPageWaiting,
} from "../components/CandidateRecordPage";
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
import { ClientVisibleSwitch } from "../../pipeline/components/ClientVisibleSwitch";
import { PipelineView } from "../../pipeline/components/PipelineView";
import * as talentMapApi from "../../talentmap/api/talentMapApi";
import type * as talentMapTypes from "../../talentmap/api/types";
import { TalentMapView } from "../../talentmap/components/TalentMapView";
import { useTalentMapPreferences, type CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import { TASKS_KEY_PREFIX } from "../../tasks/api/tasksApi";
import * as exportApi from "../api/exportApi";
import * as sourcingApi from "../../sourcing/api/sourcingApi";
import { FindExecutivesPanel } from "../../sourcing/components/FindExecutivesPanel";
import { formatRelativeTime } from "../../../lib/format";
import type { SourcingRun } from "../../sourcing/api/sourcingApi";
import { companiesOf, peopleOf } from "../../sourcing/lib/sourcingSummary";
import { useSourcingRun } from "../../sourcing/lib/useSourcingRun";
import * as triageApi from "../api/triageApi";
import { executiveStatusOptions as statusFilterOptions, statusKeysOf } from "../lib/executiveStatusFilter";
import { usePipelineCatalog } from "../../workspace/lib/usePipelineCatalog";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import type { AdvancedMenuSection } from "../components/AdvancedMenu";
import { CompanyDrawer, type CompanyDrawerTab } from "../components/CompanyDrawer";
import { ImportSpreadsheetDialog } from "../components/ImportSpreadsheetDialog";
import { ManageColumnsDialog } from "../components/ManageColumnsDialog";
import { DeclineCompanyDialog } from "../components/DeclineCompanyDialog";
import { RemoveCompanyDialog } from "../components/RemoveCompanyDialog";
import { TriageCompanyTable } from "../components/TriageCompanyTable";
import { TriageToolbar, type UnavailableAction } from "../components/TriageToolbar";
import {
  createTriageCompanyColumns,
  defaultTriageColumnVisibility,
  TRIAGE_SORT_FIELDS,
} from "../lib/triageCompanyColumns";
import { awaitingResearch, type TriageCompanyRow } from "../lib/triageRows";
import { OFFERED_TRIAGE_STAGES, offeredStageBySlug } from "../lib/triageStages";
import { COMPANIES_VIEW_LABELS, resolveCompaniesView, useCompaniesViews } from "../lib/useCompaniesViews";
import { useLineSelection } from "../lib/useLineSelection";
import { useExecutivesVisit } from "../lib/useExecutivesVisit";
import { useOpenRecord } from "../lib/useOpenRecord";
import { executivesOf } from "../lib/triageVocabulary";
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
/** Lines one press of Add to sequence may tick. */
const TICKED_LINES_CAP = 200;

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
 * One stage of a mandate's companies as its own page, the stage from the URL so one added to
 * {@link OFFERED_TRIAGE_STAGES} needs no router change; an unknown or unoffered slug redirects. An untriaged
 * mandate shows an empty stage, never the market.
 */
export function TriageStagePage() {
  const { stage: stageSlug } = useParams();
  const { search } = useLocation();
  const { project } = useOutletContext<ProjectOutletContext>();
  const stage = stageSlug ? offeredStageBySlug(stageSlug) : undefined;

  if (!stage) {
    return <Navigate to={`/projects/${project.id}/companies/${OFFERED_TRIAGE_STAGES[0].slug}${search}`} replace />;
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

  const stage = offeredStageBySlug(stageSlug!)!;
  const canWrite = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);

  const paging = useAddressedGridPaging();
  const { page, size: pageSize } = paging;
  /** The toolbar's search, the same in every view: the server matches it, so the three views agree. */
  const { query: search, setQuery: setSearch, addressedQuery: debouncedSearch } = useAddressedQuery();
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
  const companyPanel = useAddressedPanel("company");
  /** "Add company", which has no company to address. */
  const [addingCompany, setAddingCompany] = useState(false);
  const [heldCompany, setHeldCompany] = useState<OpenCompany | null>(null);
  const [companyAfterRecord, setCompanyAfterRecord] = useState<TriageCompany | null>(null);
  const openCompany = (company: TriageCompany, tab?: CompanyDrawerTab) => {
    setHeldCompany({ company, tab });
    companyPanel.open(company.id);
  };
  const closeCompany = () => {
    setAddingCompany(false);
    companyPanel.close();
  };
  const [pendingRemoval, setPendingRemoval] = useState<TriageCompany | null>(null);
  const [pendingDecline, setPendingDecline] = useState<PendingDecline | null>(null);
  const [pendingCandidateRemoval, setPendingCandidateRemoval] = useState<Candidate | null>(null);
  const [importing, setImporting] = useState(false);
  const lineSelection = useLineSelection(TICKED_LINES_CAP);
  /** The companies Find executives opened on; null while its panel is closed. */
  const [findingAt, setFindingAt] = useState<{ scope: readonly string[]; search: boolean } | null>(null);
  const [recordBarSlot, setRecordBarSlot] = useState<HTMLDivElement | null>(null);
  const [enrolment, setEnrolment] = useState<Enrolment | null>(null);
  const [pipelineSelection, setPipelineSelection] = useState<ReadonlySet<string>>(() => new Set());
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
      if (addRequest === "company") setAddingCompany(true);
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
  // `?record=page` is the full page's old link, now the Executives view.
  const requestedView = searchParams.get("record") === "page" ? "executives" : searchParams.get("view");
  const view = resolveCompaniesView(views, requestedView, mapPreferences.view);
  const isBoard = view === "pipeline";
  const isRecordView = view === "executives";
  /** Declined is a list of what was ruled out, whose one act is Back to universe. */
  const declinedStage = stage.status === "declined";
  // A tick made on one reading of the stage must not ride silently into another.
  const [tickedInView, setTickedInView] = useState(view);
  if (tickedInView !== view) {
    setTickedInView(view);
    lineSelection.clear();
    setPipelineSelection(new Set());
  }
  const location = useLocation();
  // Only the switch remembers a view: a link that names one, a notification's opening the pipeline, is a visit.
  const handleViewChange = (next: CompaniesView) => {
    setMapPreferences({ view: next });
    // The entry left is named for its view first: unnamed, Back would land on it reading the view just remembered.
    if (!searchParams.has("view")) {
      setSearchParams(
        (current) => {
          const params = new URLSearchParams(current);
          params.set("view", view);
          params.delete("record");
          return params;
        },
        { replace: true, state: location.state },
      );
    }
    setSearchParams((current) => {
      const params = new URLSearchParams(current);
      params.set("view", next);
      for (const key of ["candidate", "record", ...OPEN_RECORD_COMPANIONS, "company"]) params.delete(key);
      return params;
    });
  };

  const { profile, setProfile, stepProfile, focusTab } = useOpenRecord(project.id, !isBoard);
  const openCandidateId = profile?.candidate?.id ?? null;
  const {
    entry: executivesEntry,
    enter: enterExecutives,
    leave: leaveExecutives,
  } = useExecutivesVisit(isRecordView, openCandidateId);
  const [markedCandidateId, setMarkedCandidateId] = useState<string | null>(null);
  if (openCandidateId && openCandidateId !== markedCandidateId) setMarkedCandidateId(openCandidateId);
  const profileCompany = profile?.company ?? null;

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
      paging.reset();
      // A tick made on one reading of the stage must not ride silently into another.
      lineSelection.clear();
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
   * Removing a company takes its people with it, and adding one changes which people the grid should
   * be asking about — so the two caches move together on every write, which is what
   * {@link useProjectRowsChanged} is. The columns are not part of that and stay here: only
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
    // A move changes which companies a stage lists and its badges, not only its people; an edit changes the lines.
    if (
      kinds.some((kind) => kind.startsWith("company-") || kind === "candidate-moved" || kind === "candidate-edited")
    ) {
      refreshEveryStage();
    }
    // A renamed company is renamed on everyone mapped there.
    if (kinds.some((kind) => kind.startsWith("candidate-") || kind === "company-edited")) {
      refreshPeople();
    }
    if (kinds.includes("executive-sourcing")) {
      void queryClient.invalidateQueries({ queryKey: sourcingApi.sourcingKeys.latest(project.id) });
      void queryClient.invalidateQueries({ queryKey: sourcingApi.sourcingKeys.suggestions(project.id) });
    } else if (kinds.includes("executive-sourcing-progress")) {
      void queryClient.invalidateQueries({ queryKey: sourcingApi.sourcingKeys.latest(project.id) });
    }
    // An executive's drawer opened here carries their Outreach fold and next action.
    if (kinds.includes("outreach") || kinds.includes("task")) {
      refreshOutreachActivity(queryClient, project.id);
    }
    if (kinds.includes("task")) {
      void queryClient.invalidateQueries({ queryKey: TASKS_KEY_PREFIX });
    }
    // Both halves are points on the globe. Free while the grid is the view: the map's reads are
    // disabled there, and an inactive query is marked stale rather than refetched. Outreach, tasks and
    // a Find executives search (which files nobody) move neither half.
    if (kinds.some((kind) => kind !== "outreach" && kind !== "task" && !kind.startsWith("executive-sourcing"))) {
      refreshMap();
    }
  };

  const streamIsLive = useProjectStream(project.id, refreshWhatMoved);

  /** Offered on the universe only, and only where the deployment has a people search. */
  const sourcing = useSourcingRun(project.id, canWrite && stage.status === "inUniverse", streamIsLive);
  const findExecutivesOffered = canWrite && stage.status === "inUniverse" && sourcing.offered;
  const findExecutivesCap = sourcing.config?.maxCompaniesPerRun ?? 0;
  const mailbox = useMailbox(canWrite);
  const emailOffered = canWrite && mailbox.data?.offered === true && !isArchived(project);
  /** Ticked companies' or executives' people go to Add to sequence; never from Declined. */
  const addToSequenceOffered = emailOffered && stage.status !== "declined";
  const selectionOffered = view === "table" && (findExecutivesOffered || addToSequenceOffered);
  const tickedCompanies = lineSelection.companyIds;

  /**
   * Find executives takes the ticked companies, up to the run's cap, counted once each whatever lines name them.
   * While a search runs or its results wait it opens on that search; otherwise nothing ticked, or too many, is said.
   */
  const findExecutivesUnavailable = (): string | undefined => {
    if (sourcing.button.kind !== "idle") return undefined;
    if (view === "map" || isRecordView) return "Tick companies on the Table to find executives there";
    if (tickedCompanies.length === 0) return `Tick up to ${companiesOf(findExecutivesCap)} to find executives there`;
    if (tickedCompanies.length > findExecutivesCap) {
      return `Find executives works on ${companiesOf(findExecutivesCap)} at most — ${tickedCompanies.length} are ticked`;
    }
    return undefined;
  };
  const handleFindExecutives = () => {
    const fits = tickedCompanies.length > 0 && tickedCompanies.length <= findExecutivesCap;
    setFindingAt({ scope: fits ? tickedCompanies : [], search: true });
  };

  /**
   * Degraded-mode poll: only while the project stream is down and a visible capture is still being researched.
   * A ref, not the queries: react-query evaluates this before the people queries are declared (use-before-init).
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
    enabled: view === "table" || isRecordView,
    // Paging without blanking the grid, which would make every page turn look like a reload.
    placeholderData: keepPreviousData,
    refetchInterval: researchPoll,
  });

  const totalCount = lines.data?.totalCount;
  const lastPage = Math.max(0, Math.ceil((totalCount ?? 0) / pageSize) - 1);

  visiblePeople.current = (lines.data?.lines ?? [])
    .map((line) => line.candidate)
    .filter((candidate): candidate is Candidate => candidate !== null);
  const stepper = stepperAlong(
    view === "table" || isRecordView,
    visiblePeople.current,
    profile?.candidate ?? null,
    stepProfile,
    isRecordView ? "page" : "peek",
    isRecordView
      ? executivesEntry
        ? { onToggle: leaveExecutives, label: `Back to ${COMPANIES_VIEW_LABELS[executivesEntry.from].label}` }
        : undefined
      : openCandidateId
        ? { onToggle: () => enterExecutives("table", openCandidateId) }
        : undefined,
  );
  const profileCompanyId = profile?.candidate?.triageCompanyId ?? null;
  const profileCompanyRow = profileCompanyId
    ? (lines.data?.lines.find((line) => line.company?.id === profileCompanyId)?.company ?? null)
    : null;

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
   * The points alone, polled while the server is still placing rows and stopping once none is pending — a read of
   * its own so a poll never re-sends every company and profile.
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

  /**
   * The company panel's company: as it was opened and saved since, else found among the rows on screen for a link, a
   * refresh or Forward. One on neither closes the panel, once the rows it could have been among have arrived.
   */
  const addressedCompanyId = companyPanel.value;
  const addressedHeld = addressedCompanyId !== null && heldCompany?.company.id === addressedCompanyId ? heldCompany : null;
  const shownCompany =
    addressedCompanyId === null
      ? null
      : (addressedHeld?.company ??
        lines.data?.lines.find((line) => line.company?.id === addressedCompanyId)?.company ??
        talentMap.data?.companies.find((company) => company.id === addressedCompanyId) ??
        null);
  const drawnCompany = addingCompany ? null : shownCompany;
  const companyRowsRead = view === "map" ? talentMap : view === "table" || isRecordView ? lines : null;
  const addressedCompanyGone =
    addressedCompanyId !== null &&
    shownCompany === null &&
    (companyRowsRead === null ||
      companyRowsRead.isError ||
      (companyRowsRead.isSuccess && !companyRowsRead.isPlaceholderData));
  useEffect(() => {
    if (addressedCompanyGone) companyPanel.close();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [addressedCompanyGone]);

  const rows = useMemo(() => {
    const addedBy = lines.data?.addedBy ?? {};
    return (lines.data?.lines ?? []).flatMap((line): TriageCompanyRow[] => {
      const candidateAddedBy = line.candidate ? addedBy[line.candidate.id] : undefined;
      if (line.company) return [{ company: line.company, candidate: line.candidate, candidateAddedBy }];
      return line.candidate ? [{ company: null, candidate: line.candidate, candidateAddedBy }] : [];
    });
  }, [lines.data]);
  const recordEntries = useMemo(
    () => rows.flatMap((row) => (row.candidate ? [{ candidate: row.candidate, companyLogo: row.company?.logoUrl ?? null }] : [])),
    [rows],
  );
  // The Executives view always shows someone: the first of the page, until a link or a click names another, and the
  // first of the next page once its list turns. In place, never a step: Back leaves the view rather than this pick.
  const firstOnPage = lines.isPlaceholderData ? undefined : recordEntries[0]?.candidate;
  const namedExecutive = searchParams.get("candidate");
  // One panel at a time: one write cannot close the executive's and open the company's, so the company waits for it.
  useEffect(() => {
    if (!companyAfterRecord || namedExecutive) return;
    setCompanyAfterRecord(null);
    openCompany(companyAfterRecord);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [companyAfterRecord, namedExecutive]);
  const [recordListTurnedTo, setRecordListTurnedTo] = useState<number | null>(null);
  const recordListTurned = recordListTurnedTo === page;
  const searchNarrowed = Boolean(
    debouncedSearch || debouncedQuery || debouncedExecutiveQuery || executiveStatuses.length > 0,
  );
  // Someone about to open, named or first on the page, is not an empty stage.
  const recordPageWaiting: RecordPageWaiting = lines.isError
    ? "error"
    : lines.isFetching || namedExecutive || firstOnPage
      ? "loading"
      : searchNarrowed
        ? "filtered"
        : lastPage > 0
          ? "pageEmpty"
          : "empty";
  const recordListPager = lastPage > 0 && (
    <RecordListPager
      page={page}
      lastPage={lastPage}
      onPage={(next) => {
        setRecordListTurnedTo(next);
        paging.setPage(next);
      }}
    />
  );
  useEffect(() => {
    if (!isRecordView || !firstOnPage || (namedExecutive && !recordListTurned)) return;
    if (recordListTurned) setRecordListTurnedTo(null);
    stepProfile(firstOnPage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isRecordView, namedExecutive, firstOnPage?.id, recordListTurned]);

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

  /**
   * Declining takes everyone mapped at the company, whatever stage they stand at, so it reads them first: nobody, and
   * the company goes at once; anybody, and the dialog says how many go with it.
   */
  const requestMove = async (company: TriageCompany, status: TriageCompanyStatus) => {
    markBusy(company.id);
    if (status !== "declined") {
      move.mutate({ company, status });
      return;
    }
    let everyone: CandidatesPage;
    try {
      everyone = await candidatesApi.getCandidates(project.id, { triageCompanyIds: [company.id] });
    } catch (error) {
      clearBusy(company.id);
      toast.error(messageFor(error));
      return;
    }
    const executiveIds =
      everyone.totalCount > everyone.candidates.length ? undefined : everyone.candidates.map((candidate) => candidate.id);
    if (everyone.totalCount === 0) {
      move.mutate({ company, status, executiveIds: [] });
      return;
    }
    clearBusy(company.id);
    setPendingDecline({ company, executiveIds, count: everyone.totalCount });
  };

  /** Who a company's removal takes with it, and whether a person's removal could take their company too. */
  const executivesAtRemoval = useExecutivesAtCompany(project.id, pendingRemoval?.id ?? null);
  const executivesAtCandidateCompany = useExecutivesAtCompany(
    project.id,
    pendingCandidateRemoval?.triageCompanyId ?? null,
  );

  /** Someone was mapped at the company after its count was read: read it again, so the dialog says who goes. */
  const recountWhenRefused = (error: unknown) => {
    if (codeOf(error) === "TRIAGE_COMPANY_HAS_EXECUTIVES") {
      void queryClient.invalidateQueries({ queryKey: candidatesApi.CANDIDATES_KEY_PREFIX(project.id) });
    }
    toast.error(messageFor(error));
  };

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
    mutationFn: ({ company, executives }: { company: TriageCompany; executives: number }) =>
      triageApi.deleteTriageCompany(project.id, company.id, { expectedExecutives: executives }),
    onSuccess: (_result, { company, executives }) => {
      refreshEverything();
      setPendingRemoval(null);
      toast(
        executives > 0
          ? `${company.companyName} and ${executivesOf(executives)} removed from this mandate`
          : `${company.companyName} removed from this mandate`,
      );
    },
    onError: recountWhenRefused,
    onSettled: (_data, _error, { company }) => clearBusy(company.id),
  });

  const consentMode = useConsentMode();
  const asksToShare = consentMode.on && !!consentMode.firm?.shareWithClient;
  const vocabulary = useWorkspaceVocabulary();
  const consentRegister = useMutation({
    mutationFn: () => shareConsentApi.saveConsentRegister(project.id),
    onSuccess: () =>
      toast.success(`Downloading the consent register for ${project.positionTitle} as a CSV — recorded in the audit log`),
    onError: (error) => toast.error(messageFor(error)),
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
    mutationFn: ({ candidate, withCompany }: { candidate: Candidate; withCompany: boolean }) =>
      withCompany
        ? candidatesApi.deleteCandidate(project.id, candidate.id, { withCompany })
        : candidatesApi.deleteCandidate(project.id, candidate.id),
    onSuccess: (_result, { candidate, withCompany }) => {
      refreshEverything();
      setPendingCandidateRemoval(null);
      setProfile(null);
      // Their cached read would otherwise come back on Back, showing someone the server no longer holds.
      queryClient.removeQueries({ queryKey: candidatesApi.CANDIDATE_KEY(project.id, candidate.id), exact: true });
      toast(
        withCompany
          ? `${candidate.fullName} and ${candidate.companyName} removed from this mandate`
          : `${candidate.fullName} removed from this mandate`,
      );
    },
    onError: recountWhenRefused,
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
    const ids = lineSelection.candidateIds;
    setEnrolment({
      scope: { candidateIds: ids },
      source: `From the ${ids.length === 1 ? "executive" : `${ids.length} executives`} you ticked on ${stage.label}`,
    });
  };

  /** The toolbar's Add to sequence, greyed with the way in until something is ticked; the map ticks nothing. */
  const toolbarEnrolUnavailable = (): UnavailableAction | undefined => {
    if (view === "map") return { reason: "Add to sequence from Table or Pipeline" };
    if (view === "pipeline") {
      return pipelineSelection.size > 0 ? undefined : { reason: "Tick executives on the board first" };
    }
    if (lineSelection.candidateIds.length > 0) return undefined;
    return { reason: lineSelection.size > 0 ? "The ticked rows have no executives yet" : "Tick executives first" };
  };

  /** What Advanced ▾ holds on Table and Map (`Position.dc.html`'s `advancedItems`): only what that view can use. */
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
          ...(canWrite && !declinedStage
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
    }
    if (addToSequenceOffered && view === "table") {
      const ticked = lineSelection.candidateIds.length;
      sections.push({
        label: "Outreach",
        items: [
          {
            key: "sequence",
            label: "Add to sequence…",
            icon: ICONS.outreach,
            disabled: ticked === 0,
            hint: ticked > 0 ? `${ticked} ticked` : "Tick executives first",
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
        ...(canWrite && asksToShare
          ? [
              {
                key: "consentRegister",
                label: "Consent register",
                icon: ICONS.shield,
                disabled: consentRegister.isPending,
                hint: `Who agreed to be shared with the ${vocabulary.unitLower} · CSV`,
                onSelect: () => consentRegister.mutate(),
              },
            ]
          : []),
      ],
    });
    return sections;
  };

  // A page that outlives its rows — the last company on page 3 was moved away — would otherwise sit
  // on an empty grid with no way back but the pager.
  useEffect(() => {
    if (totalCount !== undefined && !lines.isPlaceholderData) paging.clampTo(totalCount);
  }, [totalCount, lines.isPlaceholderData, paging]);

  const columnFilters: Record<string, DataGridColumnFilter> = {
    name: {
      label: "Company",
      value: query,
      onChange: setQuery,
      "aria-label": "Filter by company name",
    },
    executive: {
      label: "Executive",
      value: executiveQuery,
      onChange: setExecutiveQuery,
      "aria-label": "Filter by executive name",
    },
    ...(executiveStatusOptions.length > 1
      ? {
          executiveStatus: {
            kind: "check" as const,
            label: "Status",
            options: executiveStatusOptions,
            selected: executiveStatusChoice,
            onChange: setExecutiveStatusChoice,
            "aria-label": "Filter by status",
          },
        }
      : {}),
  };

  /** What the record shows and does in either frame; each frame says only where the company opens. */
  const recordProps = {
    projectId: project.id,
    candidate: profile?.candidate ?? null,
    company: profile?.company ?? null,
    customColumns: candidateColumns,
    canWrite,
    defaultCurrency: briefCompensation.data?.currency,
    focusTab,
    stepper,
    onClose: () => setProfile(null),
    // The panel stays open on what the server answered: a corrected figure shows corrected
    // before the grid has refetched, and an add moves straight on to the profile it made.
    onSaved: (saved: Candidate) => {
      setProfile({ candidate: saved, company: null });
      refreshEverything();
    },
    onDelete: canWrite && !declinedStage ? setPendingCandidateRemoval : undefined,
    // The add form's other conclusion: the research happened and nobody fit, so the panel closes
    // on the flag rather than on a saved profile.
    onMarkNoExecutiveFound:
      canWrite && profileCompany
        ? () => {
            markBusy(profileCompany.triageCompanyId);
            markNoExecutiveFound.mutate({
              id: profileCompany.triageCompanyId,
              companyName: profileCompany.companyName,
            });
            setProfile(null);
          }
        : undefined,
  };

  return (
    /* No negative margins and no viewport arithmetic: the shell gives this tab the whole main area
       and a definite height (FULL_BLEED_TABS in ProjectLayout), so the height is inherited rather
       than guessed from a hard-coded amount of chrome that any topbar change would falsify. */
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <TriageToolbar
        search={search}
        onSearch={setSearch}
        searchPlaceholder={!canWrite ? "Search companies and executives…" : "Search companies, executives, notes…"}
        filter={
          view === "table" || isRecordView ? (
            <GridFilterButton
              filters={columnFilters}
              resultLabel={totalCount === undefined ? undefined : `${totalCount} ${totalCount === 1 ? "result" : "results"}`}
            />
          ) : undefined
        }
        views={views}
        view={view}
        onViewChange={handleViewChange}
        addToSequence={
          addToSequenceOffered && !isRecordView
            ? {
                onPress: enrolTicked,
                unavailable: toolbarEnrolUnavailable(),
                count: view === "pipeline" ? pipelineSelection.size : lineSelection.candidateIds.length,
              }
            : undefined
        }
        create={
          canWrite && !isBoard && !isRecordView && !declinedStage
            ? {
                onAddExecutive: () => setProfile({ candidate: null, company: null }),
                onAddCompany: () => setAddingCompany(true),
              }
            : undefined
        }
        findExecutives={
          findExecutivesOffered && !isBoard && !isRecordView
            ? {
                onPress: handleFindExecutives,
                state: sourcing.button,
                unavailable: findExecutivesUnavailable(),
                companies: tickedCompanies.length,
                last: sourcing.run ? { summary: lastSearchSummary(sourcing.run), onOpen: () => setFindingAt({ scope: [], search: false }) } : undefined,
              }
            : undefined
        }
        advanced={isBoard || isRecordView ? undefined : advancedSections()}
        advancedDot={
          view === "table" && hiddenColumnCount > 0
            ? `${hiddenColumnCount} column${hiddenColumnCount === 1 ? "" : "s"} hidden`
            : undefined
        }
        trailing={
          isRecordView ? (
            <div ref={setRecordBarSlot} role="group" aria-label="Record steps" className="flex items-center gap-0.5" />
          ) : undefined
        }
      />

      {findExecutivesOffered && (
        <FindExecutivesPanel
          open={findingAt !== null}
          onClose={() => setFindingAt(null)}
          projectId={project.id}
          subtitle={project.positionTitle}
          scope={findingAt?.scope ?? []}
          autoSearch={findingAt?.search ?? false}
          sourcing={sourcing}
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

      {isRecordView && !profile?.candidate ? (
        <RecordPageEmpty waiting={recordPageWaiting} stageLabel={stage.label} footer={recordListPager} />
      ) : isRecordView && profile?.candidate ? (
        <CandidateRecordPage
          projectId={project.id}
          stageLabel={stage.label}
          entries={recordEntries}
          openId={profile.candidate.id}
          onOpen={stepProfile}
          onLeave={executivesEntry ? leaveExecutives : undefined}
          footer={recordListPager}
        >
          {(listButton) => (
            <CandidateDrawer
              {...recordProps}
              open
              frame="page"
              stepper={stepper && { ...stepper, lead: listButton, bar: { slot: recordBarSlot } }}
              onOpenCompany={profileCompanyRow ? () => openCompany(profileCompanyRow) : undefined}
              clientVisibility={(candidate, onSaved) => (
                <ClientVisibleSwitch
                  projectId={project.id}
                  candidateId={candidate.id}
                  fullName={candidate.fullName}
                  clientVisible={candidate.clientVisible ?? false}
                  onChanged={(clientVisible) => onSaved({ ...candidate, clientVisible })}
                />
              )}
            />
          )}
        </CandidateRecordPage>
      ) : view === "pipeline" ? (
        <div className="flex min-h-0 min-w-0 flex-1 flex-col">
          <PipelineView
            project={project}
            search={debouncedSearch}
            selectedIds={pipelineSelection}
            onToggleSelected={togglePipelineSelected}
            onExpand={(candidateId) => enterExecutives("pipeline", candidateId)}
          />
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
          onOpenCompany={(company) => openCompany(company)}
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
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-3 p-2">
        <TriageCompanyTable
          rows={rows}
          openCandidateId={markedCandidateId}
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
          emptyMessage={searchNarrowed ? "No companies match that search." : stage.emptyMessage}
          columnFilters={columnFilters}
          onEditColumn={
            declinedStage
              ? undefined
              : (customColumnId) => {
                  setEditColumnId(customColumnId);
                  setManagingColumns(true);
                }
          }
          onDeleteColumn={
            canWrite && !declinedStage
              ? (customColumnId) => {
                  const column = customColumns.find((entry) => entry.id === customColumnId);
                  if (column) deleteColumn.mutate(column);
                }
              : undefined
          }
          onMove={(company, status) => void requestMove(company, status)}
          onDelete={setPendingRemoval}
          onAddExecutive={(company) =>
            setProfile({
              candidate: null,
              company: { triageCompanyId: company.id, companyName: company.companyName },
            })
          }
          onOpenCompanyNotes={(company) => openCompany(company, "notes")}
          onChangeCandidateStatus={(candidate, status) => {
            markBusy(candidate.id);
            changeCandidateStatus.mutate(
              { candidateId: candidate.id, name: candidate.fullName, status, previous: candidate.status },
              { onSettled: () => clearBusy(candidate.id) },
            );
          }}
          onEditCandidate={(candidate) => setProfile({ candidate, company: null })}
          onRemoveCandidate={setPendingCandidateRemoval}
          onMoveCandidate={(candidate, status) => {
            markBusy(candidate.id);
            moveCandidate.mutate({ candidate, status });
          }}
          onOpenCompany={(company) => openCompany(company)}
          onFindExecutives={
            findExecutivesOffered ? (company) => setFindingAt({ scope: [company.id], search: true }) : undefined
          }
          busyIds={busyIds}
          canWrite={canWrite}
          stageReadOnly={declinedStage}
          selection={
            selectionOffered
              ? { isTicked: lineSelection.isTicked, onToggle: lineSelection.toggle, onToggleAll: lineSelection.toggleAll }
              : undefined
          }
        />

        {lines.data?.truncated && (
          <p role="status" className="flex-none font-mono text-[11.5px] text-u-text3">
            This stage is larger than one read can sort, so not every company or executive is listed. Search or filter to narrow it.
          </p>
        )}

        <PaginationBar
          page={page}
          size={pageSize}
          totalCount={totalCount}
          onPage={paging.setPage}
          onSize={paging.setSize}
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
        open={addingCompany || shownCompany !== null}
        projectId={project.id}
        company={drawnCompany}
        initialTab={addressedHeld?.tab ?? "overview"}
        landingStatus={stage.status}
        customColumns={companyColumns}
        canWrite={canWrite}
        onClose={closeCompany}
        onSaved={(saved) => {
          if (saved && drawnCompany?.id === saved.id) {
            setHeldCompany({ company: withSavedFacts(drawnCompany, saved), tab: addressedHeld?.tab });
          }
          refreshEverything();
        }}
        onMove={(company, status) => {
          closeCompany();
          void requestMove(company, status);
        }}
        onDelete={(company) => {
          closeCompany();
          setPendingRemoval(company);
        }}
        // One panel at a time: the company's closes as the new executive's opens on it.
        onAddExecutive={(company) => {
          closeCompany();
          setProfile({
            candidate: null,
            company: { triageCompanyId: company.id, companyName: company.companyName },
          });
        }}
        onMarkNoExecutiveFound={(company) => {
          markBusy(company.id);
          markNoExecutiveFound.mutate(company);
        }}
        markingNoExecutiveFound={!!drawnCompany && busyIds.has(drawnCompany.id)}
        onFindExecutives={
          findExecutivesOffered
            ? (company) => {
                closeCompany();
                setFindingAt({ scope: [company.id], search: false });
              }
            : undefined
        }
      />

      <CandidateDrawer
        {...recordProps}
        open={profile !== null && !(isRecordView && profile.candidate)}
        onOpenCompany={
          profileCompanyRow
            ? () => {
                setProfile(null);
                setCompanyAfterRecord(profileCompanyRow);
              }
            : undefined
        }
      />

      <DeclineCompanyDialog
        company={pendingDecline?.company ?? null}
        executiveCount={pendingDecline?.count ?? 0}
        declining={move.isPending}
        onCancel={() => setPendingDecline(null)}
        onConfirm={(company) => {
          markBusy(company.id);
          move.mutate(
            { company, status: "declined", executiveIds: pendingDecline?.executiveIds },
            { onSettled: () => setPendingDecline(null) },
          );
        }}
      />

      <RemoveCompanyDialog
        company={pendingRemoval}
        executiveCount={executivesAtRemoval}
        removing={remove.isPending}
        onCancel={() => setPendingRemoval(null)}
        onConfirm={(company) => {
          markBusy(company.id);
          remove.mutate({ company, executives: executivesAtRemoval ?? 0 });
        }}
      />

      <RemoveCandidateDialog
        candidate={pendingCandidateRemoval}
        soleAtCompany={executivesAtCandidateCompany === 1}
        removing={removeCandidate.isPending}
        onCancel={() => setPendingCandidateRemoval(null)}
        onConfirm={(candidate, { withCompany }) => removeCandidate.mutate({ candidate, withCompany })}
      />
      {changeCandidateStatus.dialog}
    </div>
  );
}

/** Previous and Next along the grid's people in the grid's order, and the peek's way into the Executives view. */
function stepperAlong(
  offered: boolean,
  people: readonly Candidate[],
  open: Candidate | null,
  onStep: (candidate: Candidate) => void,
  frame: RecordStepper["frame"],
  toggle: { onToggle: () => void; label?: string } | undefined,
): RecordStepper | undefined {
  if (!offered || !open) return undefined;
  const at = people.findIndex((candidate) => candidate.id === open.id);
  // Someone the grid no longer shows — moved stage, another page — keeps the frame switch, with nowhere to step.
  const previous = at < 0 ? undefined : people[at - 1];
  const next = at < 0 ? undefined : people[at + 1];
  return {
    frame,
    onToggleFrame: toggle?.onToggle,
    toggleLabel: toggle?.label,
    onPrevious: previous ? () => onStep(previous) : undefined,
    onNext: next ? () => onStep(next) : undefined,
    position: at < 0 ? undefined : { index: at, total: people.length },
  };
}

/** Who Add to sequence was asked for: the ticked companies' executives, or the ticked executives. */
interface Enrolment {
  scope: ComponentProps<typeof EnrolDialog>["scope"];
  source: string;
}

/** A company waiting on the decline dialog, with everyone mapped at it — undefined ids when one read could not fit them. */
interface PendingDecline {
  company: TriageCompany;
  executiveIds?: string[];
  count: number;
}

interface CompanyMove {
  company: TriageCompany;
  status: TriageCompanyStatus;
  /** Who moves with it; read from the company's old stage when not named. */
  executiveIds?: string[];
  isUndo?: boolean;
}

/**
 * The panel's company after its own save. Kept rather than reread from the grid, it went on showing the facts it
 * opened with, and the next edit, a full replace, wrote them back. A write's answer carries none of the staff reads'
 * fields, so those stay as the panel had them.
 */
function withSavedFacts(held: TriageCompany, saved: TriageCompany): TriageCompany {
  return {
    ...saved,
    noteCount: held.noteCount,
    addedAt: held.addedAt,
    addedByUserId: held.addedByUserId,
    addedByName: held.addedByName,
    addedByAvatarUrl: held.addedByAvatarUrl,
  };
}

/** The company the panel was opened on, as saved since. */
interface OpenCompany {
  company: TriageCompany;
  /** The panel's tab to open on; its Overview when not said. */
  tab?: CompanyDrawerTab;
}

/** "4 companies · 6 executives found · 2 hours ago", or how far a search in progress has got. */
function lastSearchSummary(run: SourcingRun): string {
  if (run.status === "QUEUED" || run.status === "RUNNING") {
    return `Searching ${companiesOf(run.companiesTotal)} · ${run.companiesDone} done`;
  }
  const when = formatRelativeTime(run.finishedAt ?? run.requestedAt);
  return `${companiesOf(run.companiesTotal)} · ${peopleOf(run.suggested)} found · ${when === "active now" ? "just now" : when}`;
}
