import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSelectionFeature,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
} from "@tanstack/react-table";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGridCell, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { formatMoney } from "../../../lib/format";
import type { DiscoveredCompany, DiscoverySource } from "../api/types";

/**
 * The columns of an AI Research answer.
 *
 * <p>Its own set rather than {@link companyColumns} widened, and the reason is in the data: an
 * answer is one capped list sorted by fit where the market grid is one server-sorted page of tens of
 * thousands, and a discovered row may have no Apollo id to key on. Sharing the type would make every
 * market cell conditional to carry two columns that only exist here.
 *
 * <p>No column sorts. The server returns the answer in the order it means, fit first, and a client
 * sort over a capped list would reorder a sample as though it were the market. The feature is still
 * registered because {@link DataGrid} asks every column whether it can sort.
 */
export const discoveredTableFeatures = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSelectionFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
});

const helper = createColumnHelper<typeof discoveredTableFeatures, DiscoveredCompany>();

/** Same pill shape the Companies grid's Source column uses, so the two read as one product. */
const PILL =
  "inline-flex items-center rounded-[4px] px-[6px] py-[2px] font-mono text-[10px] font-bold uppercase tracking-[0.04em]";

/** Where the figures came from. */
const SOURCE_BADGES: Record<DiscoverySource, { label: string; className: string }> = {
  universe: { label: "In universe", className: "text-sky bg-sky-dim" },
  researched: { label: "Researched", className: "text-green bg-green-dim" },
  web: { label: "Web", className: "text-text2 bg-line-soft" },
};

export const discoveredColumns = helper.columns([
  helper.accessor("companyName", {
    id: "name",
    header: "Company",
    enableHiding: false,
    enableSorting: false,
    meta: { share: 24, min: 256 },
    cell: (info) => (
      <span className="flex min-w-0 items-center gap-2.5">
        <CompanyLogo name={info.getValue()} logo={info.row.original.logoUrl} size={28} />
        <TruncatedText
          value={info.getValue()}
          className="font-sans text-[13px] font-medium text-text"
        />
      </span>
    ),
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 76 },
    cell: (info) => {
      const company = info.row.original;
      return (
        <span className="flex justify-start gap-1">
          <CompanyLink
            url={company.website}
            icon={<Icon d={ICONS.globe} size={13} />}
            label="website"
            companyName={company.companyName}
            reserve
          />
          <CompanyLink
            url={company.companyLinkedinUrl}
            icon={<NetworkMark network="linkedin" size={14} />}
            label="LinkedIn"
            companyName={company.companyName}
            reserve
          />
        </span>
      );
    },
  }),

  helper.display({
    id: "fit",
    header: "Fit",
    enableSorting: false,
    meta: { share: 0, min: 44 },
    // The one number on this grid the model produced, and it is not a claim about the company: it
    // scores relevance to the question that was asked.
    cell: (info) => (
      <span className="font-sans text-[13px] font-semibold text-text2">
        {info.row.original.fit ?? "—"}
      </span>
    ),
  }),

  helper.display({
    id: "provenance",
    header: "Source",
    enableSorting: false,
    meta: { share: 0, min: 112 },
    cell: (info) => {
      const company = info.row.original;
      const badge = SOURCE_BADGES[company.source];
      return (
        <span className="flex items-center gap-1.5">
          <span className={`${PILL} ${badge.className}`}>{badge.label}</span>
          {company.alreadyInMandate && (
            <span className={`${PILL} text-amber bg-amber-dim`}>Added</span>
          )}
        </span>
      );
    },
  }),

  helper.accessor("companyCountry", {
    id: "country",
    header: "Country",
    enableSorting: false,
    meta: { share: 12, min: 82 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("industry", {
    id: "sector",
    header: "Sector",
    enableSorting: false,
    meta: { share: 14, min: 100 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("annualRevenue", {
    id: "revenue",
    header: "Revenue",
    enableSorting: false,
    meta: { share: 11, min: 82 },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("numEmployees", {
    id: "employees",
    header: "Employees",
    enableSorting: false,
    meta: { share: 9, min: 74 },
    cell: (info) => <DataGridCell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("foundedYear", {
    id: "founded",
    header: "Founded",
    enableSorting: false,
    meta: { share: 5, min: 84 },
    cell: (info) => <DataGridCell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor("reason", {
    id: "notes",
    header: "Notes",
    enableSorting: false,
    meta: { share: 20, min: 140 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),
]);

/** The Company column stays on screen while the rest scrolls, exactly as the market grid's does. */
export const DISCOVERED_COLUMN_PINNING: ColumnPinningState = { start: ["name"], end: [] };
