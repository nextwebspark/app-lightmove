import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
} from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui";
import {
  LOCAL_ROW_MODELS,
  DATA_GRID_FEATURES,
  GRID_ICON_BUTTON,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareNumber, compareText } from "../../../lib/gridSortFns";
import type { StaffRole, TeamMember } from "../api/types";
import { ProjectRoleChips } from "../components/ProjectRoleChips";

/** What the Roles and Manage cells need, supplied per render rather than baked into the column defs. */
export interface ProjectTeamTableMeta {
  viewerUserId: string | null;
  canManage: boolean;
  /** The last lead standing: the server refuses to demote or unseat them, so the row says so first. */
  soleLeadMemberId: string | null;
  busyMemberId: string | null;
  onChangeRole: (member: TeamMember, role: StaffRole) => void;
  onRemove: (member: TeamMember) => void;
}

/** A mandate's team is a handful of seats, held on the project itself, so the grid sorts them here. */
export const projectTeamTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...LOCAL_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as ProjectTeamTableMeta,
});

const helper = createColumnHelper<typeof projectTeamTableFeatures, TeamMember>();

/** The one staff role a seat holds. CLIENT beside it is an attach, not a seat, and is not shown here. */
export function staffRoleOf(member: TeamMember): StaffRole {
  return member.projectRoles.includes("LEAD") ? "LEAD" : "RESEARCHER";
}

const SOLE_LEAD_TITLE = "A mandate must keep a lead — make someone else lead first";

export const projectTeamColumns = helper.columns([
  helper.accessor("fullName", {
    id: "member",
    header: "Member",
    enableHiding: false,
    meta: { share: 30, min: 220 },
    sortFn: (a, b) => compareText(a.original.fullName, b.original.fullName),
    cell: (info) => {
      const member = info.row.original;
      const isSelf = member.userId === info.table.options.meta?.viewerUserId;
      return (
        <span className="flex min-w-0 items-center gap-2.5">
          <Avatar
            id={member.memberId}
            name={member.fullName}
            src={member.avatarUrl}
            size="lg"
            className="size-8"
          />
          <span className="min-w-0">
            <TruncatedText
              value={member.fullName}
              className="block font-sans text-[13.5px] font-medium text-text"
            />
            {isSelf && <span className="mt-0.5 block font-mono text-[11px] text-text3">You</span>}
          </span>
        </span>
      );
    },
  }),

  helper.display({
    id: "roles",
    header: "Roles on this project",
    enableHiding: false,
    meta: { share: 50, min: 240 },
    // Leads first: the row that owns the mandate reads before the rows that work it.
    sortFn: (a, b) =>
      compareNumber(
        staffRoleOf(a.original) === "LEAD" ? 0 : 1,
        staffRoleOf(b.original) === "LEAD" ? 0 : 1,
      ),
    cell: (info) => {
      const member = info.row.original;
      const meta = info.table.options.meta;
      return (
        <ProjectRoleChips
          memberName={member.fullName}
          role={staffRoleOf(member)}
          canManage={meta?.canManage ?? false}
          isSoleLead={meta?.soleLeadMemberId === member.memberId}
          pending={meta?.busyMemberId === member.memberId}
          onChange={(role) => meta?.onChangeRole(member, role)}
        />
      );
    },
  }),

  helper.display({
    id: "manage",
    header: "Manage",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 96 },
    cell: (info) => {
      const meta = info.table.options.meta;
      return meta ? <TeamSeatManageControl member={info.row.original} meta={meta} /> : null;
    },
  }),
]);

/**
 * The one control that unseats a member, or says why it cannot: the last lead standing is locked,
 * because the server would refuse. Spelled once and rendered from both the grid's Manage column and
 * the card below `md`, so the invariant cannot drift between the two.
 */
export function TeamSeatManageControl({
  member,
  meta,
}: {
  member: TeamMember;
  meta: ProjectTeamTableMeta;
}) {
  if (meta.soleLeadMemberId === member.memberId) {
    return (
      <span title={SOLE_LEAD_TITLE} className="grid size-9 place-items-center text-text3 lg:size-6">
        <Icon d={ICONS.lock} size={15} />
      </span>
    );
  }
  if (!meta.canManage) return null;
  return (
    <button
      type="button"
      title="Remove from project"
      aria-label={`Remove ${member.fullName}`}
      disabled={meta.busyMemberId === member.memberId}
      onClick={() => meta.onRemove(member)}
      className={`${GRID_ICON_BUTTON} hover:bg-red-dim hover:text-red disabled:opacity-50`}
    >
      <Icon d={ICONS.trash} size={15} />
    </button>
  );
}

export const PROJECT_TEAM_SORT_FIELDS = ["member", "roles"] as const;

export type ProjectTeamSortField = (typeof PROJECT_TEAM_SORT_FIELDS)[number];

export const PROJECT_TEAM_COLUMN_PINNING: ColumnPinningState = { start: ["member"], end: [] };
