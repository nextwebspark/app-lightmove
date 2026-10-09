import type { ColumnVisibilityState, OnChangeFn, PaginationState } from "@tanstack/react-table";
import { Avatar } from "../../../components/ui";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import { titleCase } from "../../../lib/format";
import { useAuth } from "../../auth/AuthProvider";
import type { Member } from "../api/types";
import {
  MEMBER_COLUMN_PINNING,
  memberColumns,
  memberTableFeatures,
  OtherRoles,
  YouMark,
  type MemberSortField,
} from "../lib/memberColumns";
import { MemberRoleSelect, RemoveMemberButton } from "./MemberManagement";

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
  /** An admin's roster: a role picker and a remove on every row. */
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
  const { user } = useAuth();
  const table = useDataGridTable<typeof memberTableFeatures, Member, MemberSortField>({
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
    meta: { activeCount, canManage, currentUserId: user?.id },
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
          isSelf={member.userId === user?.id}
        />
      )}
    />
  );
}

function MemberCard({
  member,
  activeCount,
  canManage,
  isSelf,
}: {
  member: Member;
  activeCount: number;
  canManage: boolean;
  isSelf: boolean;
}) {
  return (
    <div className="rounded-[10px] border border-u-border-strong bg-u-surface p-3">
      <div className="flex items-center gap-2.5">
        <Avatar id={member.memberId} name={member.fullName} src={member.avatarUrl} />
        <div className="min-w-0 flex-1">
          <div className="flex min-w-0 items-center gap-1.5">
            <span className="truncate text-[13px]">{member.fullName}</span>
            {isSelf && <YouMark />}
          </div>
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
          <span className="flex items-center gap-2">
            <MemberRoleSelect member={member} />
            <OtherRoles roles={member.roles} />
          </span>
          <RemoveMemberButton member={member} labelled />
        </div>
      )}
    </div>
  );
}
