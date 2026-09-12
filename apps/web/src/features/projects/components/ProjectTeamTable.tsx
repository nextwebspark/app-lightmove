import type { OnChangeFn, PaginationState } from "@tanstack/react-table";
import { Avatar } from "../../../components/ui";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { TeamMember } from "../api/types";
import {
  PROJECT_TEAM_COLUMN_PINNING,
  projectTeamColumns,
  projectTeamTableFeatures,
  staffRoleOf,
  TeamSeatManageControl,
  type ProjectTeamSortField,
  type ProjectTeamTableMeta,
} from "../lib/projectTeamColumns";
import { ProjectRoleChips } from "./ProjectRoleChips";

/**
 * The staff half of Team & access: who staffs this mandate and the one role each holds, over the
 * shared {@link DataGrid}. The client contacts below it are not a table — the mockup gives them no
 * header, and a contact carries a lifecycle status rather than a role — so they stay a list.
 */
export function ProjectTeamTable({
  staff,
  meta,
  sort,
  onSortChange,
  layout,
  onLayoutChange,
  pagination,
  onPaginationChange,
}: {
  staff: TeamMember[];
  meta: ProjectTeamTableMeta;
  sort: GridSort<ProjectTeamSortField>;
  onSortChange: (sort: GridSort<ProjectTeamSortField>) => void;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  pagination: PaginationState;
  onPaginationChange: OnChangeFn<PaginationState>;
}) {
  const table = useDataGridTable<typeof projectTeamTableFeatures, TeamMember, ProjectTeamSortField>({
    features: projectTeamTableFeatures,
    columns: projectTeamColumns,
    data: staff,
    getRowId: (member) => member.memberId,
    pinning: PROJECT_TEAM_COLUMN_PINNING,
    sort,
    onSortChange,
    layout,
    onLayoutChange,
    pagination,
    onPaginationChange,
    meta,
  });

  return (
    <DataGrid
      table={table}
      label="Project team"
      fit="content"
      layout={layout}
      onLayoutChange={onLayoutChange}
      // The team arrives on the project, which the layout resolved before this tab could render.
      loading={false}
      error={false}
      errorMessage="The team could not be loaded. Refresh, or check you still have access to this mandate."
      emptyMessage="No one staffs this mandate yet."
      renderCard={(member) => <TeamSeatCard member={member} meta={meta} />}
    />
  );
}

function TeamSeatCard({ member, meta }: { member: TeamMember; meta: ProjectTeamTableMeta }) {
  return (
    <div className="flex flex-col gap-3 rounded-[10px] border border-line bg-panel p-3.5">
      <div className="flex items-center gap-2.5">
        <Avatar id={member.memberId} name={member.fullName} src={member.avatarUrl} size="lg" className="size-8" />
        <div className="min-w-0 flex-1">
          <div className="truncate text-[13.5px] font-medium">{member.fullName}</div>
          {member.userId === meta.viewerUserId && (
            <div className="mt-0.5 font-mono text-[11px] text-text3">You</div>
          )}
        </div>
        <TeamSeatManageControl member={member} meta={meta} />
      </div>
      <ProjectRoleChips
        memberName={member.fullName}
        role={staffRoleOf(member)}
        canManage={meta.canManage}
        isSoleLead={meta.soleLeadMemberId === member.memberId}
        pending={meta.busyMemberId === member.memberId}
        onChange={(role) => meta.onChangeRole(member, role)}
      />
    </div>
  );
}
