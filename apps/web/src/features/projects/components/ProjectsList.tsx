import type { ColumnVisibilityState, OnChangeFn } from "@tanstack/react-table";
import type { PaginationState } from "@tanstack/react-table";
import { HealthDot, StagePill } from "../../../components/ui";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import { formatDate } from "../../../lib/format";
import type { Project } from "../api/types";
import {
  leadOf,
  PROJECT_COLUMN_PINNING,
  projectColumns,
  projectTableFeatures,
  TeamStack,
  type ProjectSortField,
} from "../lib/projectColumns";

/** The mandate list: the shared grid on a wide screen, a stack of cards below `md`. */
export function ProjectsList({
  projects,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
  pagination,
  onPaginationChange,
  error,
  emptyMessage,
  onOpen,
}: {
  projects: Project[];
  sort: GridSort<ProjectSortField>;
  onSortChange: (sort: GridSort<ProjectSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  pagination: PaginationState;
  onPaginationChange: OnChangeFn<PaginationState>;
  error: boolean;
  emptyMessage: string;
  onOpen: (projectId: string) => void;
}) {
  const table = useDataGridTable<typeof projectTableFeatures, Project, ProjectSortField>({
    features: projectTableFeatures,
    columns: projectColumns,
    data: projects,
    getRowId: (project) => project.id,
    pinning: PROJECT_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    pagination,
    onPaginationChange,
  });

  return (
    <DataGrid
      table={table}
      label="Projects"
      fit="content"
      layout={layout}
      onLayoutChange={onLayoutChange}
      // The page gates on `isPending` before it renders this, so a row model is never in flight here.
      loading={false}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access."
      emptyMessage={emptyMessage}
      onRowClick={(project) => onOpen(project.id)}
      renderCard={(project) => <ProjectCard project={project} onOpen={() => onOpen(project.id)} />}
    />
  );
}

function ProjectCard({ project, onOpen }: { project: Project; onOpen: () => void }) {
  return (
    <button
      type="button"
      onClick={onOpen}
      className="flex w-full flex-col gap-2.5 rounded-[10px] border border-line bg-panel p-3.5 text-left transition hover:bg-panel2"
    >
      <div className="flex items-start gap-2">
        <div className="min-w-0 flex-1">
          <div className="truncate font-mono text-[11.5px] font-medium text-text3">
            {project.clientName}
          </div>
          <div className="mt-0.5 text-[13.5px] font-semibold text-text">{project.positionTitle}</div>
        </div>
        <HealthDot health={project.health} />
      </div>

      <div className="flex flex-wrap items-center gap-2">
        <StagePill stage={project.stage} />
        <span className="font-mono text-[11px] text-text3">
          Lead · {leadOf(project.team)?.fullName ?? "—"}
        </span>
      </div>

      <div className="flex items-center gap-2.5 border-t border-line-soft pt-2.5">
        <TeamStack team={project.team} />
        <span className="ml-auto font-mono text-[11px] text-text2">
          <b className="font-semibold text-text">{project.companies}</b> cos ·{" "}
          <b className="font-semibold text-text">{project.candidates}</b> cand
        </span>
        <span className="font-mono text-[11px] text-text3">{formatDate(project.targetDate)}</span>
      </div>
    </button>
  );
}
