import type {
  ColumnVisibilityState,
  OnChangeFn,
  PaginationState,
} from "@tanstack/react-table";
import { Avatar } from "../../../components/ui";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import { titleCase } from "../../../lib/format";
import type { Member } from "../api/types";
import { MemberRoleSelect, RemoveMemberButton } from "./MemberManagement";
import {
  MEMBER_COLUMN_PINNING,
  memberColumns,
  memberTableFeatures,
  type MemberSortField,
} from "../lib/memberColumns";

/** The roster: the shared grid on a wide screen, a stack of cards below `md`. */
export function MembersList({
  members,
  activeCount,
  canManage,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
  pagination,
  onPaginationChange,
}: {
  members: Member[];
  /** How many mandates a member carries — a fact about the project list, which the roster does not carry. */
  activeCount: (memberId: string) => number;
  canManage: boolean;
  sort: GridSort<MemberSortField>;
  onSortChange: (sort: GridSort<MemberSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  pagination: PaginationState;
  onPaginationChange: OnChangeFn<PaginationState>;
}) {
  const table = useDataGridTable<
    typeof memberTableFeatures,
    Member,
    MemberSortField
  >({
    features: memberTableFeatures,
    columns: memberColumns,
    data: members,
    getRowId: (member) => member.memberId,
    pinning: MEMBER_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility: { ...columnVisibility, remove: canManage },
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    pagination,
    onPaginationChange,
    meta: { activeCount, canManage },
  });

  return (
    <DataGrid
      table={table}
      label="Team"
      fit="content"
      layout={layout}
      onLayoutChange={onLayoutChange}
      // The page answers the refused read before it renders this.
      loading={false}
      error={false}
      errorMessage="The roster could not be loaded. Refresh, or check you still have access."
      emptyMessage="No one is on the roster yet."
      renderCard={(member) => (
        <MemberCard
          member={member}
          activeCount={activeCount(member.memberId)}
          canManage={canManage}
        />
      )}
    />
  );
}

function MemberCard({
  member,
  activeCount,
  canManage,
}: {
  member: Member;
  activeCount: number;
  canManage: boolean;
}) {
  return (
    <div className="rounded-[10px] border border-u-border-strong bg-u-surface p-3">
      <div className="flex items-center gap-2.5">
        <Avatar
          id={member.memberId}
          name={member.fullName}
          src={member.avatarUrl}
        />
        <div className="min-w-0 flex-1">
          <div className="truncate text-[13px]">{member.fullName}</div>
          <div className="truncate font-mono text-[11px] text-u-text3">
            {canManage ? member.email : `${member.roles.map(titleCase).join(" · ")} · ${member.email}`}
          </div>
        </div>
        <div className="flex-none font-mono text-[11px] text-u-text3">
          {activeCount} active {activeCount === 1 ? "position" : "positions"}
        </div>
      </div>
      {canManage && (
        <div className="mt-2.5 flex items-center justify-between gap-2 border-t border-u-border pt-2.5">
          <MemberRoleSelect member={member} />
          <RemoveMemberButton member={member} />
        </div>
      )}
    </div>
  );
}
