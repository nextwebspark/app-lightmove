import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import {
  LOCAL_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGridCell,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareNumber, compareText } from "../../../lib/gridSortFns";
import { initials } from "../../../lib/format";
import type { Client, ClientRepStatus, ViewerSummary } from "../api/types";
import { BusinessUnitGlyph } from "../components/BusinessUnitGlyph";
import { openPositionsLabel } from "./openPositions";

/** The registry is one query of tens of rows, so the grid sorts and pages it itself. */
export const clientTableFeatures = tableFeatures({
  ...DATA_GRID_FEATURES,
  ...LOCAL_ROW_MODELS,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof clientTableFeatures, Client>();

export const clientColumns = helper.columns([
  helper.accessor("name", {
    id: "name",
    header: "Business unit",
    enableHiding: false,
    meta: { share: 30, min: 220 },
    sortFn: (a, b) => compareText(a.original.name, b.original.name),
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <BusinessUnitGlyph size={26} />
        <span className="min-w-0">
          <TruncatedText
            value={info.getValue()}
            className="block font-sans text-[13px] font-semibold text-u-text"
          />
          <TruncatedText
            value={openPositionsLabel(info.row.original.activeMandates)}
            className="block font-mono text-[11px] text-u-text3"
          />
        </span>
      </span>
    ),
  }),

  helper.display({
    id: "contacts",
    header: "Hiring managers",
    enableSorting: false,
    meta: { share: 0, min: 148 },
    cell: (info) => <RepStack contacts={info.row.original.contacts} />,
  }),

  helper.accessor("activeMandates", {
    id: "mandates",
    header: "Open positions",
    meta: { share: 0, min: 104 },
    sortFn: (a, b) => compareNumber(a.original.activeMandates, b.original.activeMandates),
    cell: (info) => <DataGridCell value={String(info.getValue())} />,
  }),

  helper.display({
    id: "viewers",
    header: "Viewers",
    enableSorting: false,
    meta: { share: 0, min: 140 },
    cell: (info) => <ViewerCell viewers={info.row.original.viewers} />,
  }),
]);

export const CLIENT_SORT_FIELDS = ["name", "mandates"] as const;

export type ClientSortField = (typeof CLIENT_SORT_FIELDS)[number];

export const CLIENT_COLUMN_VISIBILITY: ColumnVisibilityState = {};

export const CLIENT_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };

const REP_TINT: Record<ClientRepStatus, string> = {
  ACTIVE: "bg-u-direct-tint text-u-direct",
  INVITED: "bg-u-accent-tint text-u-accent",
};

export function RepStack({ contacts }: { contacts: { fullName: string; status: ClientRepStatus }[] }) {
  if (contacts.length === 0) {
    return <span className="font-mono text-xs text-u-text3">—</span>;
  }
  const shown = contacts.slice(0, 4);
  const overflow = contacts.length - shown.length;
  return (
    <span className="flex items-center">
      {shown.map((contact, index) => (
        <span
          key={`${contact.fullName}-${index}`}
          title={contact.fullName}
          className={`grid size-6 place-items-center rounded-full border-2 border-u-surface font-mono text-[10px] font-semibold ${
            REP_TINT[contact.status]
          } ${index > 0 ? "-ml-[7px]" : ""}`}
        >
          {initials(contact.fullName)}
        </span>
      ))}
      {overflow > 0 && (
        <span className="-ml-[7px] grid size-6 place-items-center rounded-full border-2 border-u-surface bg-u-raised font-mono text-[10px] font-semibold text-u-text3">
          +{overflow}
        </span>
      )}
    </span>
  );
}

export function ViewerCell({ viewers }: { viewers: ViewerSummary }) {
  const dot = viewers.active > 0 ? "bg-u-direct" : viewers.invited > 0 ? "bg-u-accent" : "bg-u-border-strong";
  const label =
    viewers.active === 0 && viewers.invited === 0
      ? "None"
      : [
          viewers.active > 0 ? `${viewers.active} active` : null,
          viewers.invited > 0 ? `${viewers.invited} invited` : null,
        ]
          .filter(Boolean)
          .join(" · ");
  return (
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap font-mono text-xs text-u-text2">
      <span className={`size-[7px] rounded-full ${dot}`} />
      {label}
    </span>
  );
}
