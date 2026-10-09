import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, buttonClassName, EmptyState, TableSkeleton } from "../../../components/ui";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { ListToolbar } from "../../../components/ui/ListToolbar";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridPaging } from "../../../lib/useGridPaging";
import { useGridSort, WORKSPACE_SCOPE } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";
import * as projectsApi from "../api/projectsApi";
import { GettingStartedCard } from "../../gettingstarted/components/GettingStartedCard";
import { NewProjectModal } from "../components/NewProjectModal";
import { ProjectDrawer } from "../components/ProjectDrawer";
import { ProjectsList } from "../components/ProjectsList";
import {
  PROJECT_COLUMN_VISIBILITY,
  PROJECT_SORT_FIELDS,
  projectColumns,
  type ProjectSortField,
} from "../lib/projectColumns";
import { CHIPS, filterProjects, isActive, type ChipKey } from "../lib/filtering";

const PROJECT_LAYOUT_COLUMNS = layoutColumnsOf(projectColumns);
const HIDEABLE_PROJECT_COLUMNS = hideableColumnsOf(projectColumns);

const DEFAULT_CHIP: ChipKey = "active";

const DEFAULT_PROJECT_SORT = { field: "target", direction: "asc" } as const;

/**
 * The workspace home: the mandate list under My/All views, with the search box, stage chips and
 * sortable columns from the mockup. All filtering is client-side over one query — a firm's mandate
 * list is tens of rows.
 */
export function ProjectsPage({ view }: { view: "my" | "all" }) {
  const { user } = useAuth();
  const vocabulary = useWorkspaceVocabulary();
  // The registry and roster are staff surfaces a pure client can't read; the server already scopes
  // their project list to the mandates they're attached to, so that list IS "my projects" for them.
  const clientOnly = isPureClient(user?.workspace?.roles ?? []);
  // In the address, so the back link from a position returns to the list as it was left.
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get("q") ?? "";
  const chip = chipOf(searchParams.get("stage"));
  const setFilter = (key: "q" | "stage", value: string, isDefault: boolean) =>
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        if (isDefault) next.delete(key);
        else next.set(key, value);
        return next;
      },
      { replace: true },
    );
  const setQuery = (next: string) => setFilter("q", next, next === "");
  const setChip = (next: ChipKey) => setFilter("stage", next, next === DEFAULT_CHIP);
  const [openProjectId, setOpenProjectId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [sort, setSort] = useGridSort<ProjectSortField>(
    "projects",
    WORKSPACE_SCOPE,
    PROJECT_SORT_FIELDS,
    DEFAULT_PROJECT_SORT,
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "projects",
    WORKSPACE_SCOPE,
    PROJECT_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout("projects", PROJECT_LAYOUT_COLUMNS);
  const paging = useGridPaging();

  const { data: projects = [], isPending, isError } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });
  // Gated on a known user: until the session resolves we can't tell staff from client, and firing the
  // staff-only queries for a client would 403.
  const { data: clients = [] } = useQuery({
    queryKey: clientsApi.CLIENTS_KEY,
    queryFn: clientsApi.clients,
    enabled: Boolean(user) && !clientOnly,
  });
  const { data: members = [], isPending: membersPending, isError: membersRefused } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
    enabled: Boolean(user) && !clientOnly,
  });

  const myMemberId = members.find((m) => m.userId === user?.id)?.memberId;

  const rows = useMemo(
    () => filterProjects(projects, { view: clientOnly ? "all" : view, myMemberId, chip, query }),
    [projects, view, clientOnly, myMemberId, chip, query],
  );

  // Narrowing the list returns to the first page — staying on page 4 of a filter that now matches
  // two mandates shows an empty grid over a non-empty result — and a list that shrank under the
  // reader, from a refetch, is clamped back onto its last page.
  const { reset: resetPage, clampTo } = paging;
  useEffect(() => {
    resetPage();
  }, [resetPage, view, chip, query, sort]);
  useEffect(() => {
    clampTo(rows.length);
  }, [clampTo, rows.length]);

  const openProject = projects.find((p) => p.id === openProjectId) ?? null;
  const filtered = query.trim() !== "" || chip !== DEFAULT_CHIP;
  const unfilteredCount = useMemo(
    () => filterProjects(projects, { view: clientOnly ? "all" : view, myMemberId, chip: "allstages", query: "" }).length,
    [projects, view, clientOnly, myMemberId],
  );
  // Nothing on My positions because nobody has seated them yet — not because a filter found nothing.
  const firstUse =
    view === "my" &&
    !clientOnly &&
    myMemberId !== undefined &&
    !projects.some((project) => project.team.some((seat) => seat.memberId === myMemberId));

  const newProjectButton = (
    <Button onClick={() => setModalOpen(true)} className="!px-3.5 !py-[7px] !text-[13px]">
      <Icon d={ICONS.plus} size={15} />
      New position
    </Button>
  );

  // While the list is in flight, `projects` is still the [] default — without this gate the
  // "create your first project" empty state flashes before the table arrives.
  if (isPending) {
    return (
      <>
        <PageHeader
          title={view === "my" ? "My positions" : "All positions"}
          subtitle={`workspace ${user?.workspace?.name ?? ""}`}
          action={newProjectButton}
        />
        <TableSkeleton
          columns={["Position", "Stage", "Health", "Team", "Target", "Pipeline"]}
        />
      </>
    );
  }

  // A refused read falls back to the [] default too, and "create your first project" over a 403
  // states as fact that the firm has none. Say what happened instead.
  if (isError) {
    return (
      <>
        <PageHeader
          title={view === "my" ? "My positions" : "All positions"}
          subtitle={`workspace ${user?.workspace?.name ?? ""}`}
        />
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load the positions"
          body="You may no longer have access to them, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }

  const header = (
    <PageHeader
      title={view === "my" ? "My positions" : "All positions"}
      subtitle={`workspace ${user?.workspace?.name ?? ""}`}
      action={clientOnly ? undefined : newProjectButton}
    />
  );

  if (projects.length === 0) {
    if (clientOnly) {
      return (
        <>
          {header}
          <EmptyState
            icon={<Icon d={ICONS.briefcase} size={24} />}
            title="No positions shared with you yet"
            body={`When ${vocabulary.positionSharer ?? user?.workspace?.name ?? "your search firm"} shares a position with you, it'll appear here.`}
          />
        </>
      );
    }
    const startCopy =
      "A position is one role you're filling. You'll write the brief, pick target companies, map the executives at them, and reach out — all in one place.";
    return (
      <>
        {header}
        <GettingStartedCard
          onOpenPosition={() => setModalOpen(true)}
          intro={
            <>
              <h2 className="text-title font-semibold">Start your first search</h2>
              <p className="mt-1 max-w-[620px] text-body text-u-text2">{startCopy}</p>
            </>
          }
          fallback={
            <EmptyState icon={<Icon d={ICONS.briefcase} size={24} />} title="Start your first search" body={startCopy} />
          }
        />
        {modalOpen && (
          <NewProjectModal open onClose={() => setModalOpen(false)} clients={clients} />
        )}
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={view === "my" ? "My positions" : "All positions"}
        subtitle={`${countLabel(rows.length, unfilteredCount, filtered)} · workspace ${user?.workspace?.name ?? ""}`}
        action={clientOnly ? undefined : newProjectButton}
      />

      {view === "my" && !clientOnly && <GettingStartedCard onOpenPosition={() => setModalOpen(true)} />}

      {!firstUse && (
        <ListToolbar
          query={query}
          onQueryChange={setQuery}
          placeholder={`Search ${vocabulary.unitLower} or position…`}
          chips={CHIPS}
          activeChip={chip}
          onChipChange={setChip}
          trailing={
            <ColumnPicker
              columns={HIDEABLE_PROJECT_COLUMNS}
              visibility={columnVisibility}
              defaults={PROJECT_COLUMN_VISIBILITY}
              onChange={setColumnVisibility}
              onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
            />
          }
        />
      )}

      {rows.length === 0 && view === "my" && !clientOnly && membersPending ? (
        // Whose positions are "mine" is read off the roster; until it arrives, nothing can be said about none.
        <TableSkeleton columns={["Position", "Stage", "Health", "Team", "Target", "Pipeline"]} />
      ) : rows.length === 0 && view === "my" && !clientOnly && membersRefused ? (
        // Without the roster nothing can be said about which positions are yours — least of all that there are none.
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't tell which positions are yours"
          body="The team list didn't load. Reload the page, or browse every position in the meantime."
        >
          <Link to="/all" className={buttonClassName("secondary")}>
            Browse all positions
          </Link>
        </EmptyState>
      ) : rows.length === 0 ? (
        <ListEmptyState
          firstUse={firstUse}
          openCount={projects.filter(isActive).length}
          filtered={filtered}
          onClearFilters={() => setSearchParams(new URLSearchParams(), { replace: true })}
          onShowAllStages={() => setChip("allstages")}
        />
      ) : (
        <div className="flex flex-col gap-3">
          <ProjectsList
            projects={rows}
            sort={sort}
            onSortChange={setSort}
            columnVisibility={columnVisibility}
            onColumnVisibilityChange={setColumnVisibility}
            layout={layout}
            onLayoutChange={setLayout}
            pagination={paging.pagination}
            onPaginationChange={paging.onPaginationChange}
            emptyMessage="No positions match."
            onOpen={setOpenProjectId}
          />
          <PaginationBar
            page={paging.page}
            size={paging.size}
            totalCount={rows.length}
            onPage={paging.setPage}
            onSize={paging.setSize}
            autoHide
          />
        </div>
      )}

      <ProjectDrawer project={openProject} onClose={() => setOpenProjectId(null)} />

      {modalOpen && (
        <NewProjectModal open onClose={() => setModalOpen(false)} clients={clients} />
      )}
    </>
  );
}

function ListEmptyState({
  firstUse,
  openCount,
  filtered,
  onClearFilters,
  onShowAllStages,
}: {
  firstUse: boolean;
  openCount: number;
  filtered: boolean;
  onClearFilters: () => void;
  onShowAllStages: () => void;
}) {
  if (firstUse) {
    const positions = openCount === 1 ? "1 open position" : `${openCount} open positions`;
    return (
      <EmptyState
        icon={<Icon d={ICONS.briefcase} size={24} />}
        title="You're not on any position yet"
        body={
          openCount > 0
            ? `Your team has ${positions}. Ask a lead to add you, or browse them.`
            : "Your team has no open positions right now. Open one with New position to get started."
        }
      >
        {openCount > 0 && (
          <Link to="/all" className={buttonClassName("primary")}>
            Browse open positions ({openCount})
          </Link>
        )}
      </EmptyState>
    );
  }
  if (filtered) {
    return (
      <EmptyState icon={<Icon d={ICONS.searchX} size={24} />} title="No positions match" body="Nothing fits this search and stage.">
        <Button variant="secondary" onClick={onClearFilters}>
          Clear filters
        </Button>
      </EmptyState>
    );
  }
  return (
    <EmptyState
      icon={<Icon d={ICONS.briefcase} size={24} />}
      title="No active positions"
      body="Every position here is delivered or closed."
    >
      <Button variant="secondary" onClick={onShowAllStages}>
        Show all stages
      </Button>
    </EmptyState>
  );
}


/** "3 positions", or under a filter "0 of 3 positions" — so a narrowed list never reads as having none. */
function countLabel(shown: number, total: number, filtered: boolean): string {
  const noun = (filtered ? total : shown) === 1 ? "position" : "positions";
  return filtered ? `${shown} of ${total} ${noun}` : `${shown} ${noun}`;
}

function chipOf(value: string | null): ChipKey {
  return CHIPS.find((candidate) => candidate.key === value)?.key ?? DEFAULT_CHIP;
}
