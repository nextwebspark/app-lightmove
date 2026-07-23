import { useQuery } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState, TableSkeleton } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import * as clientsApi from "../../clients/api/clientsApi";
import * as workspaceApi from "../../workspace/api/workspaceApi";
import * as projectsApi from "../api/projectsApi";
import { NewProjectModal } from "../components/NewProjectModal";
import { ProjectDrawer } from "../components/ProjectDrawer";
import { ProjectsTable } from "../components/ProjectsTable";
import { CHIPS, filterProjects, sortProjects, type ChipKey, type SortKey } from "../lib/filtering";

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
  const [sortKey, setSortKey] = useState<SortKey>("date");
  const [sortDirection, setSortDirection] = useState<1 | -1>(1);
  const [openProjectId, setOpenProjectId] = useState<string | null>(null);
  const [modalOpen, setModalOpen] = useState(false);

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
    () =>
      sortProjects(
        filterProjects(projects, { view: clientOnly ? "all" : view, myMemberId, chip, query }),
        sortKey,
        sortDirection,
      ),
    [projects, view, clientOnly, myMemberId, chip, query, sortKey, sortDirection],
  );

  const onSort = (key: SortKey) => {
    if (key === sortKey) setSortDirection((d) => (d === 1 ? -1 : 1));
    else {
      setSortKey(key);
      setSortDirection(1);
    }
  };

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
          body="A project holds one search mandate end to end — brief, company universe, sourcing and candidates."
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

      <div className="mb-3.5 flex flex-wrap items-center gap-2.5">
        <div className="flex w-[300px] items-center gap-2 rounded-lg border border-line bg-panel2 px-[11px] py-[7px]">
          <Icon d={ICONS.search} size={14} className="text-text3" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search client or position…"
            className="w-full bg-transparent font-mono text-[13px] text-text outline-none placeholder:text-text3"
          />
        </div>

        <div className="flex flex-wrap gap-1.5">
          {CHIPS.map(({ key, label }) => (
            <button
              key={key}
              type="button"
              onClick={() => setChip(key)}
              className={`rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-text ${
                chip === key ? "border-amber bg-amber-dim text-amber" : "border-line text-text2"
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      </div>

      <ProjectsTable
        projects={rows}
        sortKey={sortKey}
        sortDirection={sortDirection}
        onSort={onSort}
        onOpen={setOpenProjectId}
      />

      {rows.length === 0 && (
        <div className="p-12 text-center font-mono text-[13px] text-text3">
          {clientOnly ? "No projects match. Clear filters." : "No projects match. Clear filters or create a new project."}
        </div>
      )}

      <ProjectDrawer
        project={openProject}
        members={members}
        canManageTeam={!clientOnly}
        onClose={() => setOpenProjectId(null)}
      />

      {modalOpen && (
        <NewProjectModal open onClose={() => setModalOpen(false)} clients={clients} />
      )}
    </>
  );
}
