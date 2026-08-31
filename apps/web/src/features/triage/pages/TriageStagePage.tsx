import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Navigate, useOutletContext, useParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { PAGE_SIZE } from "../../../lib/paging";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridSort, type GridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import * as candidatesApi from "../../candidates/api/candidatesApi";
import type { Candidate, CandidatesPage } from "../../candidates/api/types";
import {
  CandidateDrawer,
  type CandidateCompanyContext,
} from "../../candidates/components/CandidateDrawer";
import { RemoveCandidateDialog } from "../../candidates/components/RemoveCandidateDialog";
import * as customColumnsApi from "../../customcolumns/api/customColumnsApi";
import type { CustomColumn } from "../../customcolumns/api/types";
import { canExecuteProjectWork } from "../../projects/lib/access";
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
import { toTriageRows } from "../lib/triageRows";
import { stageBySlug, TRIAGE_STAGES } from "../lib/triageStages";

/**
 * The grid's built-in columns for the layout hook. A mandate's own custom columns are deliberately
 * absent: `useGridLayout` is keyed per user and per grid, *not* per project — a column layout is a
 * working habit rather than a property of one mandate — so remembering a width for a column only one
 * project has would carry a phantom into every other project's grid.
 */
const TRIAGE_LAYOUT_COLUMNS = layoutColumnsOf(createTriageCompanyColumns([]));

/** A stable empty array: a fresh `[]` per render would rebuild every column def on every render. */
const EMPTY_CUSTOM_COLUMNS: CustomColumn[] = [];

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
  // Keyed on the stage so switching pages resets the search box and the page number with it, rather
  // than carrying "page 4 of the universe" into a shortlist that has one page.
  return <TriageStage key={stage.slug} />;
}

function TriageStage() {
  const { stage: stageSlug } = useParams();
  const { project } = useOutletContext<ProjectOutletContext>();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const toast = useToast();

  const stage = stageBySlug(stageSlug!)!;
  const canWrite = canExecuteProjectWork(project, user?.id, user?.workspace?.roles);

  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [debouncedQuery, setDebouncedQuery] = useState("");
  const [busyId, setBusyId] = useState<string | null>(null);
  const [openCompany, setOpenCompany] = useState<OpenCompany | null>(null);
  const [pendingRemoval, setPendingRemoval] = useState<TriageCompany | null>(null);
  const [profile, setProfile] = useState<OpenProfile | null>(null);
  const [pendingCandidateRemoval, setPendingCandidateRemoval] = useState<Candidate | null>(null);
  const [importing, setImporting] = useState(false);
  const [managingColumns, setManagingColumns] = useState(false);
  const [sort, setSort] = useGridSort("companies", project.id, TRIAGE_SORT_FIELDS, DEFAULT_SORT);

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
    // Bumped from "companies" when Source became hidden-by-default. `useColumnVisibility` merges a
    // stored map *over* the defaults — deliberately, so a column added later takes its declared
    // default — which means everyone who had opened this grid already had `source: true` written down
    // and would never have seen the new default. A new namespace resets this grid's remembered layout
    // once, which is the price of a default that otherwise could not take effect.
    "companies.v2",
    project.id,
    defaultVisibility,
  );
  const [layout, setLayout] = useGridLayout("companies", TRIAGE_LAYOUT_COLUMNS);

  // A keystroke should narrow the list, not fire a request per character.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedQuery(query), 300);
    return () => clearTimeout(timer);
  }, [query]);

  // Any change to what is being asked returns to the first page. Staying on page 4 of a search that
  // now matches two companies shows an empty grid over a non-empty result.
  useEffect(() => setPage(0), [debouncedQuery, sort]);

  const companies = useQuery({
    queryKey: triageApi.TRIAGE_KEY(project.id, stage.status, page, PAGE_SIZE, debouncedQuery, sort),
    queryFn: ({ signal }) =>
      triageApi.getTriageCompanies(
        project.id,
        stage.status,
        page,
        PAGE_SIZE,
        debouncedQuery,
        sort,
        signal,
      ),
    // Paging without blanking the grid, which would make every page turn look like a reload.
    placeholderData: keepPreviousData,
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
   * number this side must not try to guess — see {@link candidatesApi.getCandidates}.
   */
  const mappedPeople = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(project.id, { triageCompanyIds: companyIds }),
    queryFn: ({ signal }) =>
      candidatesApi.getCandidates(project.id, { triageCompanyIds: companyIds }, signal),
    enabled: companyIds.length > 0,
    placeholderData: keepPreviousData,
  });

  const totalCount = companies.data?.totalCount;
  const lastPage = Math.max(0, Math.ceil((totalCount ?? 0) / PAGE_SIZE) - 1);

  /**
   * Executives whose employer is not in the mandate's universe at all. They belong to the mandate
   * rather than to any company, so they sit after the companies on the universe's last page — the one
   * place a reader reaches by scrolling to the end of the mapping. Grouping the grid by company is
   * where they eventually get a heading of their own; until then, invisible would be worse.
   */
  const unmappedPeople = useQuery({
    queryKey: candidatesApi.CANDIDATES_KEY(project.id, { unmapped: true }),
    queryFn: ({ signal }) =>
      candidatesApi.getCandidates(project.id, { unmapped: true }, signal),
    enabled: stage.status === "inUniverse" && !debouncedQuery && page === lastPage,
  });

  const rows = useMemo(
    () =>
      toTriageRows(
        companies.data?.companies ?? [],
        mappedPeople.data?.candidates ?? [],
        unmappedPeople.data?.candidates ?? [],
      ),
    [companies.data, mappedPeople.data, unmappedPeople.data],
  );

  /**
   * What the two people reads could not fit. Both are capped by the server, and a mapping that ran
   * past the cap would otherwise render fewer lines per company with nothing saying so — a talent map
   * that looks complete and is not, on the screen whose whole job is showing what has been mapped.
   *
   * <p>Stated rather than hidden, for the same reason `totalLabel` below refuses to print a count it
   * has not read yet.
   */
  const unlisted = [
    peopleNotShown(mappedPeople.data, "at these companies"),
    peopleNotShown(unmappedPeople.data, "with no company in this mandate"),
  ].filter((line): line is string => line !== null);

  /**
   * Every write invalidates the whole prefix rather than this stage's key. A move changes two stages
   * and all three counts, and a page that refreshed only the list it was looking at would show the
   * company gone and the shortlist badge still one short.
   */
  const refreshEveryStage = () =>
    void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });

  /**
   * Removing a company unmaps its people rather than deleting them, and adding one changes which
   * people the grid should be asking about — so the two caches move together on every write.
   */
  const refreshEverything = () => {
    refreshEveryStage();
    void queryClient.invalidateQueries({
      queryKey: candidatesApi.CANDIDATES_KEY_PREFIX(project.id),
    });
  };

  const move = useMutation({
    mutationFn: ({ company, status }: { company: TriageCompany; status: TriageCompanyStatus }) =>
      triageApi.updateTriageCompany(project.id, company.id, { status }),
    onSuccess: (_result, { company, status }) => {
      refreshEveryStage();
      toast(`${company.companyName} moved to ${MOVE_LABELS[status]}`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: () => setBusyId(null),
  });

  const remove = useMutation({
    mutationFn: (company: TriageCompany) => triageApi.deleteTriageCompany(project.id, company.id),
    onSuccess: (_result, company) => {
      refreshEverything();
      setPendingRemoval(null);
      toast(`${company.companyName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: () => setBusyId(null),
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

  // A page that outlives its rows — the last company on page 3 was moved away — would otherwise sit
  // on an empty grid with no way back but the pager.
  useEffect(() => {
    if (page > lastPage) setPage(lastPage);
  }, [page, lastPage]);

  return (
    /* No negative margins and no viewport arithmetic: the shell gives this tab the whole main area
       and a definite height (FULL_BLEED_TABS in ProjectLayout), so the height is inherited rather
       than guessed from a hard-coded amount of chrome that any topbar change would falsify. */
    <div className="flex min-h-0 flex-1 flex-col">
      <TriageToolbar
        projectId={project.id}
        counts={companies.data?.counts}
        query={query}
        onQuery={setQuery}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        customColumns={customColumns}
        onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
        onAddCompany={() => setOpenCompany({ company: null })}
        onAddExecutive={() => setProfile({ candidate: null, company: null })}
        onImport={() => setImporting(true)}
        onManageColumns={() => setManagingColumns(true)}
        canWrite={canWrite}
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
        onClose={() => setManagingColumns(false)}
      />

      <div className="flex min-w-0 flex-1 flex-col gap-3 p-3 sm:p-5">
        <TriageCompanyTable
          rows={rows}
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
          emptyMessage={debouncedQuery ? "No companies match that search." : stage.emptyMessage}
          onMove={(company, status) => {
            setBusyId(company.id);
            move.mutate({ company, status });
          }}
          onDelete={setPendingRemoval}
          onAddExecutive={(company) =>
            setProfile({
              candidate: null,
              company: { triageCompanyId: company.id, companyName: company.companyName },
            })
          }
          onEditCandidate={(candidate) => setProfile({ candidate, company: null })}
          onOpenCompany={(company) => setOpenCompany({ company })}
          busyId={busyId}
          canWrite={canWrite}
        />

        {unlisted.map((line) => (
          <p key={line} role="status" className="flex-none font-mono text-[11.5px] text-text3">
            {line}
          </p>
        ))}

        <PaginationBar page={page} size={PAGE_SIZE} totalCount={totalCount} onPage={setPage} />
      </div>

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
          setBusyId(company.id);
          setOpenCompany(null);
          move.mutate({ company, status });
        }}
        onDelete={(company) => {
          setOpenCompany(null);
          setPendingRemoval(company);
        }}
      />

      <CandidateDrawer
        open={profile !== null}
        projectId={project.id}
        candidate={profile?.candidate ?? null}
        company={profile?.company ?? null}
        customColumns={candidateColumns}
        canWrite={canWrite}
        onClose={() => setProfile(null)}
        onSaved={refreshEverything}
        onDelete={canWrite ? setPendingCandidateRemoval : undefined}
      />

      <RemoveCompanyDialog
        company={pendingRemoval}
        removing={remove.isPending}
        onCancel={() => setPendingRemoval(null)}
        onConfirm={(company) => {
          setBusyId(company.id);
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
