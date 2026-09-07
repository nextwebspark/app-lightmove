import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState, TableSkeleton } from "../../../components/ui";
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
import * as projectsApi from "../api/projectsApi";
import { NewProjectModal } from "../components/NewProjectModal";
import { ProjectDrawer } from "../components/ProjectDrawer";
import { ProjectsList } from "../components/ProjectsList";
import {
  PROJECT_COLUMN_VISIBILITY,
  PROJECT_SORT_FIELDS,
  projectColumns,
  type ProjectSortField,
} from "../lib/projectColumns";
import { CHIPS, filterProjects, type ChipKey } from "../lib/filtering";

const PROJECT_LAYOUT_COLUMNS = layoutColumnsOf(projectColumns);
const HIDEABLE_PROJECT_COLUMNS = hideableColumnsOf(projectColumns);

const DEFAULT_PROJECT_SORT = { field: "target", direction: "asc" } as const;

/**
 * The workspace home: the mandate list under My/All views, with the search box, stage chips and
 * sortable columns from the mockup. All filtering is client-side over one query — a firm's mandate
 * list is tens of rows.
 */
export function ProjectsPage({ view }: { view: "my" | "all" }) {
  const { user } = useAuth();
  // The registry and roster are staff surfaces a pure client can't read; the server already scopes
  // their project list to the mandates they're attached to, so that list IS "my projects" for them.
  const clientOnly = isPureClient(user?.workspace?.roles ?? []);
  const [query, setQuery] = useState("");
  const [chip, setChip] = useState<ChipKey>("active");
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

  const { data: projects = [], isPending } = useQuery({
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
  const { data: members = [] } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
    enabled: Boolean(user) && !clientOnly,
  });

  const myMemberId = members.find((m) => m.userId === user?.id)?.memberId;

  const rows = useMemo(
    () => filterProjects(projects, { view: clientOnly ? "all" : view, myMemberId, chip, query }),
    [projects, view, clientOnly, myMemberId, chip, query],
  );

  // Narrowing the list returns to the first page. Staying on page 4 of a filter that now matches two
  // mandates shows an empty grid over a non-empty result.
  const { reset: resetPage } = paging;
  useEffect(() => resetPage(), [resetPage, view, chip, query, sort]);

  const openProject = projects.find((p) => p.id === openProjectId) ?? null;

  const newProjectButton = (
    <Button onClick={() => setModalOpen(true)} className="!px-3.5 !py-[7px] !text-[13px]">
      <Icon d={ICONS.plus} size={15} />
      New project
    </Button>
  );

  // While the list is in flight, `projects` is still the [] default — without this gate the
  // "create your first project" empty state flashes before the table arrives.
  if (isPending) {
    return (
      <>
        <PageHeader
          title={view === "my" ? "My projects" : "All projects"}
          subtitle={`workspace ${user?.workspace?.name ?? ""}`}
          action={newProjectButton}
        />
        <TableSkeleton
          columns={["Client", "Position", "Stage", "Health", "Team", "Target", "Pipeline"]}
        />
      </>
    );
  }

  if (projects.length === 0) {
    if (clientOnly) {
      return (
        <EmptyState
          icon={<Icon d={ICONS.briefcase} size={24} />}
          title="No projects shared with you yet"
          body="When your search firm attaches you to a mandate, it will appear here."
        />
      );
    }
    return (
      <>
        <EmptyState
          icon={<Icon d={ICONS.briefcase} size={24} />}
          title="Create your first project"
          body="A project holds one search mandate end to end — brief, company universe, triage and candidates."
        >
          {newProjectButton}
          <div className="mt-[34px] flex items-center gap-2.5 font-mono text-[11px] font-medium uppercase tracking-[0.06em] text-text3">
            <span>Brief</span>
            <span className="opacity-50">→</span>
            <span>Universe</span>
            <span className="opacity-50">→</span>
            <span>Mapping</span>
            <span className="opacity-50">→</span>
            <span>Shortlist</span>
          </div>
        </EmptyState>
        {modalOpen && (
          <NewProjectModal open onClose={() => setModalOpen(false)} clients={clients} />
        )}
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={view === "my" ? "My projects" : "All projects"}
        subtitle={`${rows.length} ${rows.length === 1 ? "project" : "projects"} · workspace ${user?.workspace?.name ?? ""}`}
        action={clientOnly ? undefined : newProjectButton}
      />

      <ListToolbar
        query={query}
        onQuery={setQuery}
        placeholder="Search client or position…"
        chips={CHIPS}
        chip={chip}
        onChip={setChip}
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
          error={false}
          emptyMessage={
            clientOnly
              ? "No projects match. Clear filters."
              : "No projects match. Clear filters or create a new project."
          }
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

      <ProjectDrawer
        project={openProject}
        members={members}
        onClose={() => setOpenProjectId(null)}
      />

      {modalOpen && (
        <NewProjectModal open onClose={() => setModalOpen(false)} clients={clients} />
      )}
    </>
  );
}
