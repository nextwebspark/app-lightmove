import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { Link, Navigate, useOutletContext, useParams } from "react-router-dom";
import type { ProjectOutletContext } from "../../../components/layout/ProjectLayout";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useToast } from "../../../components/ui/Toast";
import { messageFor } from "../../../lib/errorCodes";
import { PAGE_SIZE } from "../../../lib/paging";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { useGridSort, type GridSort } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import { canExecuteProjectWork } from "../../projects/lib/access";
import * as triageApi from "../api/triageApi";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import { AddCompanyModal } from "../components/AddCompanyModal";
import { RemoveCompanyDialog } from "../components/RemoveCompanyDialog";
import { TriageCompanyTable } from "../components/TriageCompanyTable";
import { TriageStageSwitcher } from "../components/TriageStageSwitcher";
import { TriageToolbar } from "../components/TriageToolbar";
import {
  DEFAULT_TRIAGE_COLUMN_VISIBILITY,
  TRIAGE_SORT_FIELDS,
} from "../lib/triageCompanyColumns";
import { stageBySlug, TRIAGE_STAGES } from "../lib/triageStages";

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
  const [addOpen, setAddOpen] = useState(false);
  const [pendingRemoval, setPendingRemoval] = useState<TriageCompany | null>(null);
  const [sort, setSort] = useGridSort("companies", project.id, TRIAGE_SORT_FIELDS, DEFAULT_SORT);
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "companies",
    project.id,
    DEFAULT_TRIAGE_COLUMN_VISIBILITY,
  );

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

  /**
   * Every write invalidates the whole prefix rather than this stage's key. A move changes two stages
   * and all three counts, and a page that refreshed only the list it was looking at would show the
   * company gone and the shortlist badge still one short.
   */
  const refreshEveryStage = () =>
    void queryClient.invalidateQueries({ queryKey: triageApi.TRIAGE_KEY_PREFIX(project.id) });

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
      refreshEveryStage();
      setPendingRemoval(null);
      toast(`${company.companyName} removed from this mandate`);
    },
    onError: (error) => toast(messageFor(error)),
    onSettled: () => setBusyId(null),
  });

  const totalCount = companies.data?.totalCount;
  const lastPage = Math.max(0, Math.ceil((totalCount ?? 0) / PAGE_SIZE) - 1);

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
        query={query}
        onQuery={setQuery}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        onAddCompany={() => setAddOpen(true)}
        canWrite={canWrite}
        totalLabel={
          // Undefined while unknown: "0 companies" beside a loading grid states as fact a number
          // nobody has read yet.
          totalCount === undefined
            ? undefined
            : `${totalCount.toLocaleString()} ${totalCount === 1 ? "company" : "companies"}`
        }
      />

      <div className="flex min-w-0 flex-1 flex-col gap-3 p-3 sm:p-5">
        <div className="flex flex-none flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="font-sans text-[19px]/[1.2] font-semibold">{stage.label}</h1>
            <p className="mt-1 max-w-[640px] text-[13px] text-text2">
              {stage.description}{" "}
              <Link
                to={`/projects/${project.id}/strategy`}
                className="text-amber hover:underline"
              >
                Search the market
              </Link>{" "}
              to add more.
            </p>
          </div>
          <TriageStageSwitcher projectId={project.id} counts={companies.data?.counts} />
        </div>

        <TriageCompanyTable
          companies={companies.data?.companies ?? []}
          label={`${stage.label} companies`}
          sort={sort}
          onSortChange={setSort}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          loading={companies.isFetching}
          error={companies.isError}
          emptyMessage={debouncedQuery ? "No companies match that search." : stage.emptyMessage}
          onMove={(company, status) => {
            setBusyId(company.id);
            move.mutate({ company, status });
          }}
          onDelete={setPendingRemoval}
          busyId={busyId}
          canWrite={canWrite}
        />

        <PaginationBar page={page} size={PAGE_SIZE} totalCount={totalCount} onPage={setPage} />
      </div>

      <AddCompanyModal
        open={addOpen}
        projectId={project.id}
        landingStatus={stage.status}
        onClose={() => setAddOpen(false)}
        onAdded={refreshEveryStage}
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
    </div>
  );
}
