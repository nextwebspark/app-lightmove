import {
  createColumnHelper,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import {
  LOCAL_ROW_MODELS,
  DATA_GRID_FEATURES,
  DataGridCell,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { compareNumber, compareText } from "../../../lib/gridSortFns";
import { initials } from "../../../lib/format";
import type { Client, ClientRepStatus, ClientType, ViewerSummary } from "../api/types";

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
    header: "Client",
    enableHiding: false,
    // The floor covers a twenty-odd-character name: the mark and its gutter eat 36px before a letter.
    meta: { share: 26, min: 220 },
    sortFn: (a, b) => compareText(a.original.name, b.original.name),
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <CompanyLogo name={info.getValue()} logo={info.row.original.logoUrl} size={26} />
        <span className="min-w-0">
          <TruncatedText
            value={info.getValue()}
            className="block font-sans text-[13px] font-semibold text-text"
          />
          {locationOf(info.row.original) && (
            <TruncatedText
              value={locationOf(info.row.original)}
              className="block font-mono text-[11px] text-text3"
            />
          )}
        </span>
      </span>
    ),
  }),

  helper.accessor("type", {
    id: "type",
    header: "Type",
    meta: { share: 0, min: 108 },
    sortFn: (a, b) => compareText(a.original.type, b.original.type),
    cell: (info) => <TypePill type={info.getValue()} />,
  }),

  helper.display({
    id: "contacts",
    header: "Client contact",
    enableSorting: false,
    meta: { share: 0, min: 132 },
    cell: (info) => <RepStack contacts={info.row.original.contacts} />,
  }),

  helper.accessor("sector", {
    id: "sector",
    header: "Sector",
    meta: { share: 20, min: 140 },
    sortFn: (a, b) => compareText(a.original.sector, b.original.sector),
    cell: (info) => <DataGridCell value={info.getValue() ?? "—"} />,
  }),

  helper.accessor("activeMandates", {
    id: "mandates",
    header: "Mandates",
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

export const CLIENT_SORT_FIELDS = ["name", "type", "sector", "mandates"] as const;

export type ClientSortField = (typeof CLIENT_SORT_FIELDS)[number];

export const CLIENT_COLUMN_VISIBILITY: ColumnVisibilityState = {};

export const CLIENT_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };

const TYPE_STYLES: Record<ClientType, { label: string; className: string }> = {
  RETAINED: { label: "Retained", className: "text-sky bg-sky-dim border-transparent" },
  PROSPECT: { label: "Prospect", className: "text-text3 border-line-soft" },
};

export function TypePill({ type }: { type: ClientType }) {
  const { label, className } = TYPE_STYLES[type];
  return (
    <span
      className={`inline-flex items-center whitespace-nowrap rounded-md border px-[9px] py-[3px] font-mono text-[10.5px] font-semibold uppercase tracking-[0.06em] ${className}`}
    >
      {label}
    </span>
  );
}

const REP_TINT: Record<ClientRepStatus, string> = {
  ACTIVE: "bg-green-dim text-green",
  INVITED: "bg-amber-dim text-amber",
};

export function RepStack({ contacts }: { contacts: { fullName: string; status: ClientRepStatus }[] }) {
  if (contacts.length === 0) {
    return <span className="font-mono text-xs text-text3">—</span>;
  }
  const shown = contacts.slice(0, 4);
  const overflow = contacts.length - shown.length;
  return (
    <span className="flex items-center">
      {shown.map((contact, index) => (
        <span
          key={`${contact.fullName}-${index}`}
          title={contact.fullName}
          className={`grid size-6 place-items-center rounded-full border-2 border-panel font-mono text-[10px] font-semibold ${
            REP_TINT[contact.status]
          } ${index > 0 ? "-ml-[7px]" : ""}`}
        >
          {initials(contact.fullName)}
        </span>
      ))}
      {overflow > 0 && (
        <span className="-ml-[7px] grid size-6 place-items-center rounded-full border-2 border-panel bg-panel2 font-mono text-[10px] font-semibold text-text3">
          +{overflow}
        </span>
      )}
    </span>
  );
}

export function ViewerCell({ viewers }: { viewers: ViewerSummary }) {
  const dot = viewers.active > 0 ? "bg-green" : viewers.invited > 0 ? "bg-amber" : "bg-line";
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
    <span className="inline-flex items-center gap-1.5 whitespace-nowrap font-mono text-xs text-text2">
      <span className={`size-[7px] rounded-full ${dot}`} />
      {label}
    </span>
  );
}

/** Where the client is, city first — the subtext under its name, matching the company picker's rows. */
export function locationOf(client: Client): string {
  return [client.hqCity, client.hqCountry].filter(Boolean).join(", ");
}
