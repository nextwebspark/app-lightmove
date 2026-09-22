import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, CompanyLogo, HealthPill, ProjectTypeBadge } from "../../../components/ui";
import {
  LOCAL_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGridCell,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareDate, compareNumber, compareText } from "../../../lib/gridSortFns";
import { formatDate } from "../../../lib/format";
import { PhaseBar } from "../components/PhaseBar";
import type { MandateProgress, Project, TeamMember } from "../api/types";

/**
 * The mandate list holds every project the firm has — tens of rows, in one query — so unlike the
 * market grids it sorts and pages them itself.
 */
export const projectTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...LOCAL_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof projectTableFeatures, Project>();

/**
 * Each column's id is the token {@link PROJECT_SORT_FIELDS} allowlists and `useGridSort` remembers,
 * so a stored preference can only ever name a column that still exists.
 */
export const projectColumns = helper.columns([
  helper.accessor("clientName", {
    id: "company",
    header: "Company",
    enableHiding: false,
    meta: { share: 20, min: 180 },
    sortFn: (a, b) => compareText(a.original.clientName, b.original.clientName),
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2">
        <CompanyLogo name={info.getValue()} logo={info.row.original.clientLogoUrl} size={22} />
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
      </span>
    ),
  }),

  helper.accessor("positionTitle", {
    id: "role",
    header: "Role & type",
    enableHiding: false,
    meta: { share: 22, min: 190 },
    sortFn: (a, b) => compareText(a.original.positionTitle, b.original.positionTitle),
    cell: (info) => (
      <span className="block min-w-0">
        <TruncatedText
          value={info.getValue()}
          className="font-sans text-[13px] font-semibold text-text"
        />
        <span className="mt-1 block">
          <ProjectTypeBadge projectType={info.row.original.projectType} />
        </span>
      </span>
    ),
  }),

  helper.display({
    id: "team",
    header: "Team",
    enableSorting: false,
    meta: { share: 0, min: 116 },
    cell: (info) => <TeamStack team={info.row.original.team} />,
  }),

  helper.accessor((project) => phaseRank(project.progress), {
    id: "progress",
    header: "Assignment progress",
    meta: { share: 0, min: 168 },
    sortFn: (a, b) => compareNumber(phaseRank(a.original.progress), phaseRank(b.original.progress)),
    cell: (info) => (
      <PhaseBar
        progress={info.row.original.progress}
        projectType={info.row.original.projectType}
      />
    ),
  }),

  helper.accessor("mappingTargetDate", {
    id: "mapTarget",
    header: "Map target",
    meta: { share: 0, min: 116 },
    sortFn: (a, b) => compareDate(a.original.mappingTargetDate, b.original.mappingTargetDate),
    cell: (info) =>
      info.row.original.progress.mappingComplete ? (
        <span className="whitespace-nowrap font-mono text-[12.5px] font-semibold text-green">
          Complete ✓
        </span>
      ) : (
        <DataGridCell value={formatDate(info.getValue())} />
      ),
  }),

  helper.accessor("shortlistTargetDate", {
    id: "shortlist",
    header: "Shortlist delivery",
    meta: { share: 0, min: 130 },
    sortFn: (a, b) => compareDate(a.original.shortlistTargetDate, b.original.shortlistTargetDate),
    cell: (info) => <DataGridCell value={formatDate(info.getValue())} />,
  }),

  helper.accessor("health", {
    id: "status",
    header: "Status",
    enableSorting: false,
    meta: { share: 0, min: 108 },
    cell: (info) => <HealthPill health={info.getValue()} />,
  }),

  helper.display({
    id: "open",
    header: "",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 96 },
    cell: (info) => <OpenProjectLink project={info.row.original} />,
  }),
]);

/**
 * One number a mandate's progress sorts by. Engaging mandates rank above mapping ones whatever their
 * percentages, because a mandate working its people is further along than one still building a list.
 */
function phaseRank(progress: MandateProgress): number {
  return progress.activePhase === "ENGAGE" ? 100 + progress.engagePercent : progress.mapPercent;
}

export const PROJECT_SORT_FIELDS = ["company", "role", "progress", "mapTarget", "shortlist"] as const;

export type ProjectSortField = (typeof PROJECT_SORT_FIELDS)[number];

export const PROJECT_COLUMN_VISIBILITY: ColumnVisibilityState = {};

/** A scrolled row without its client is a line of anonymous figures, so the client travels with it. */
export const PROJECT_COLUMN_PINNING: ColumnPinningState = { start: ["company"], end: [] };

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

export function OpenProjectLink({ project }: { project: Project }) {
  return (
    <Link
      to={`/projects/${project.id}`}
      aria-label={`Open ${project.positionTitle}`}
      title={`Open ${project.positionTitle}`}
      className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[7px] border border-line px-[11px] py-[5px] text-xs font-semibold text-text2 transition hover:border-text3 hover:bg-panel hover:text-text"
    >
      Open
      <Icon d={ICONS.arrowRight} size={13} />
    </Link>
  );
}

export function leadOf(team: TeamMember[]): TeamMember | undefined {
  // Leads are plural — a mandate always has at least one, and the first is who the list names.
  return team.find((seat) => seat.projectRoles.includes("LEAD"));
}
