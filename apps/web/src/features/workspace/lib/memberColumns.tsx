import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { Avatar } from "../../../components/ui";
import {
  LOCAL_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGridCell,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareNumber, compareText } from "../../../lib/gridSortFns";
import { titleCase } from "../../../lib/format";
import type { Member } from "../api/types";

/** How many mandates a member currently carries — counted on the page, read by the column. */
interface MemberTableMeta {
  activeCount: (memberId: string) => number;
}

/** A firm's roster is one query of tens of rows, so the grid sorts and pages it itself. */
export const memberTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...LOCAL_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as MemberTableMeta,
});

const helper = createColumnHelper<typeof memberTableFeatures, Member>();

export const memberColumns = helper.columns([
  helper.accessor("fullName", {
    id: "name",
    header: "Member",
    enableHiding: false,
    meta: { share: 26, min: 220 },
    sortFn: (a, b) => compareText(a.original.fullName, b.original.fullName),
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <Avatar
          id={info.row.original.memberId}
          name={info.getValue()}
          src={info.row.original.avatarUrl}
        />
        <TruncatedText value={info.getValue()} className="font-sans text-[13px] text-text" />
      </span>
    ),
  }),

  helper.accessor("email", {
    id: "email",
    header: "Email",
    meta: { share: 26, min: 200 },
    sortFn: (a, b) => compareText(a.original.email, b.original.email),
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.display({
    id: "roles",
    header: "Workspace roles",
    enableSorting: false,
    meta: { share: 20, min: 160 },
    cell: (info) => <DataGridCell value={info.row.original.roles.map(titleCase).join(" · ")} />,
  }),

  helper.display({
    id: "projects",
    header: "Active projects",
    meta: { share: 0, min: 132 },
    // The count is the page's, not the row's — it is a fact about the project list, which the roster
    // response does not carry — so the comparator reads it back off the table's meta.
    sortFn: (a, b) =>
      compareNumber(
        a.table.options.meta?.activeCount(a.original.memberId),
        b.table.options.meta?.activeCount(b.original.memberId),
      ),
    cell: (info) => {
      const count = info.table.options.meta?.activeCount(info.row.original.memberId) ?? 0;
      return <DataGridCell value={`${count} active ${count === 1 ? "project" : "projects"}`} muted />;
    },
  }),
]);

export const MEMBER_SORT_FIELDS = ["name", "email", "projects"] as const;

export type MemberSortField = (typeof MEMBER_SORT_FIELDS)[number];

export const MEMBER_COLUMN_VISIBILITY: ColumnVisibilityState = {};

export const MEMBER_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };
