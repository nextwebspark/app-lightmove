import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { Avatar, HealthDot, StagePill } from "../../../components/ui";
import {
  CLIENT_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGridCell,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareDate, compareNumber, compareText } from "../../../lib/gridSortFns";
import { formatDate } from "../../../lib/format";
import type { Project, TeamMember } from "../api/types";
import { STAGE_ORDER } from "./filtering";

/**
 * The mandate list holds every project the firm has — tens of rows, in one query — so unlike the
 * market grids it sorts and pages them itself.
 */
export const projectTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...CLIENT_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof projectTableFeatures, Project>();

/**
 * Each column's id is the token {@link PROJECT_SORT_FIELDS} allowlists and `useGridSort` remembers,
 * so a stored preference can only ever name a column that still exists.
 */
export const projectColumns = helper.columns([
  helper.accessor("clientName", {
    id: "client",
    header: "Client",
    enableHiding: false,
    meta: { share: 18, min: 160 },
    sortFn: (a, b) => compareText(a.original.clientName, b.original.clientName),
    cell: (info) => (
      <TruncatedText
        value={info.getValue()}
        className="font-mono text-[12.5px] font-medium text-text2"
      />
    ),
  }),

  helper.accessor("positionTitle", {
    id: "position",
    header: "Position",
    enableHiding: false,
    meta: { share: 26, min: 200 },
    sortFn: (a, b) => compareText(a.original.positionTitle, b.original.positionTitle),
    cell: (info) => (
      <span className="block min-w-0">
        <TruncatedText
          value={info.getValue()}
          className="font-sans text-[13px] font-semibold text-text"
        />
        <TruncatedText
          value={`Lead · ${leadOf(info.row.original.team)?.fullName ?? "—"}`}
          className="mt-0.5 block font-mono text-[11px] text-text3"
        />
      </span>
    ),
  }),

  helper.accessor("stage", {
    id: "stage",
    header: "Stage",
    // "Outreach live" is the widest pill, and a pill that crossed the gap would sit in Health.
    meta: { share: 0, min: 148 },
    // The stages are a sequence, not an alphabet: Brief comes before Universe, not after Outreach.
    sortFn: (a, b) =>
      compareNumber(STAGE_ORDER.indexOf(a.original.stage), STAGE_ORDER.indexOf(b.original.stage)),
    cell: (info) => <StagePill stage={info.getValue()} />,
  }),

  helper.accessor("health", {
    id: "health",
    header: "Health",
    enableSorting: false,
    meta: { share: 0, min: 92 },
    cell: (info) => <HealthDot health={info.getValue()} />,
  }),

  helper.display({
    id: "team",
    header: "Team",
    enableSorting: false,
    meta: { share: 0, min: 116 },
    cell: (info) => <TeamStack team={info.row.original.team} />,
  }),

  helper.accessor("targetDate", {
    id: "target",
    header: "Target",
    meta: { share: 0, min: 104 },
    sortFn: (a, b) => compareDate(a.original.targetDate, b.original.targetDate),
    cell: (info) => <DataGridCell value={formatDate(info.getValue())} />,
  }),

  helper.display({
    id: "pipeline",
    header: "Pipeline",
    enableSorting: false,
    meta: { share: 0, min: 132 },
    cell: (info) => {
      const project = info.row.original;
      return (
        <span className="whitespace-nowrap font-mono text-xs text-text2">
          <b className="font-semibold text-text">{project.companies}</b> cos ·{" "}
          <b className="font-semibold text-text">{project.candidates}</b> cand
        </span>
      );
    },
  }),
]);

export const PROJECT_SORT_FIELDS = ["client", "position", "stage", "target"] as const;

export type ProjectSortField = (typeof PROJECT_SORT_FIELDS)[number];

export const PROJECT_COLUMN_VISIBILITY: ColumnVisibilityState = {};

/** A scrolled row without its client is a line of anonymous figures, so the client travels with it. */
export const PROJECT_COLUMN_PINNING: ColumnPinningState = { start: ["client"], end: [] };

export function TeamStack({ team }: { team: TeamMember[] }) {
  return (
    <span className="flex">
      {team.map((seat, index) => (
        <Avatar
          key={seat.memberId}
          id={seat.memberId}
          name={seat.fullName}
          src={seat.avatarUrl}
          size="sm"
          className={`border-2 border-panel ${index > 0 ? "-ml-[7px]" : ""}`}
        />
      ))}
    </span>
  );
}

export function leadOf(team: TeamMember[]): TeamMember | undefined {
  // Leads are plural — a mandate always has at least one, and the first is who the list names.
  return team.find((seat) => seat.projectRoles.includes("LEAD"));
}
