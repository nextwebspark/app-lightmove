import {
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { DataGridCell, GRID_ICON_BUTTON, type DataGridColumnLayout } from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { formatMoney, joined } from "../../../lib/format";
import type { CompanyResult, CompanySortField } from "../api/types";

/** What the Actions column needs, supplied per render rather than baked into the column defs. */
interface CompanyTableMeta {
  onAddToUniverse: (company: CompanyResult) => void;
  addingId: string | null;
}

/**
 * No pagination, filtering or sorted row model registered: all three are the server's, and a client
 * row model would re-sort the 25 rows this page holds as though they were the whole result.
 */
export const companyTableFeatures = tableFeatures({
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as CompanyTableMeta,
});

const helper = createColumnHelper<typeof companyTableFeatures, CompanyResult>();

/**
 * Each sortable column's id is its wire sort token, the same string `CompanySortField` allowlists,
 * so there is no click-to-field mapping that can drift. The columns below Notes carry no token, so
 * they are unsortable by construction and the server's ORDER BY allowlist stays the eight it was
 * written for.
 */
export const companyColumns = helper.columns([
  helper.accessor("companyName", {
    id: "name",
    header: "Company",
    enableHiding: false,
    // The floor covers a twenty-odd-character name: logo and gutters eat 54px before a letter draws.
    meta: { share: 22, min: 230 },
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
    id: "actions",
    header: "Actions",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 64 },
    cell: (info) => {
      const company = info.row.original;
      const meta = info.table.options.meta;
      return (
        <span className="flex justify-start gap-1.5">
          <button
            type="button"
            title="Add to universe"
            aria-label={`Add ${company.companyName} to universe`}
            onClick={() => meta?.onAddToUniverse(company)}
            disabled={meta?.addingId === company.apolloAccountId}
            className={`${GRID_ICON_BUTTON} disabled:opacity-40`}
          >
            <Icon d={ICONS.plus} size={14} />
          </button>
        </span>
      );
    },
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 84 },
    cell: (info) => {
      const company = info.row.original;
      return (
        <span className="flex justify-start gap-1">
          <CompanyLink url={company.website} icon={ICONS.globe} label="website" companyName={company.companyName} />
          <CompanyLink
            url={company.companyLinkedinUrl}
            icon={ICONS.linkedin}
            label="LinkedIn"
            companyName={company.companyName}
          />
          <CompanyLink
            url={company.facebookUrl}
            icon={ICONS.facebook}
            label="Facebook"
            companyName={company.companyName}
          />
          <CompanyLink url={company.twitterUrl} icon={ICONS.x} label="X" companyName={company.companyName} />
        </span>
      );
    },
  }),

  helper.display({
    id: "fit",
    header: "Fit",
    enableSorting: false,
    meta: { share: 0, min: 44 },
    // No score yet; it fills in when AI Research lands.
    cell: () => <span className="font-sans text-[13px] font-semibold text-text3">—</span>,
  }),

  helper.accessor("companyCountry", {
    id: "country",
    header: "Country",
    meta: { share: 12, min: 82 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("industry", {
    id: "sector",
    header: "Sector",
    meta: { share: 18, min: 100 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("companyCity", {
    id: "location",
    header: "City",
    meta: { share: 11, min: 82 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("annualRevenue", {
    id: "revenue",
    header: "Revenue",
    meta: { share: 11, min: 82 },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("numEmployees", {
    id: "employees",
    header: "Employees",
    meta: { share: 9, min: 74 },
    cell: (info) => <DataGridCell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("foundedYear", {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84 },
    cell: (info) => <DataGridCell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor("shortDescription", {
    id: "notes",
    header: "Notes",
    // Not in the server's sort allowlist: alphabetising a description answers no question.
    enableSorting: false,
    meta: { share: 12, min: 108 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor("companyPhone", {
    id: "phone",
    header: "Phone",
    enableSorting: false,
    meta: { share: 10, min: 120 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("companyState", {
    id: "state",
    header: "State",
    enableSorting: false,
    meta: { share: 10, min: 96 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("companyAddress", {
    id: "address",
    header: "Address",
    enableSorting: false,
    meta: { share: 16, min: 160 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor("parentCompany", {
    id: "parent",
    header: "Parent company",
    enableSorting: false,
    meta: { share: 12, min: 108 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("totalFunding", {
    id: "totalFunding",
    header: "Total funding",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("latestFunding", {
    id: "latestFunding",
    header: "Latest funding",
    enableSorting: false,
    meta: { share: 11, min: 120 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("latestFundingAmount", {
    id: "latestFundingAmount",
    header: "Latest round",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor("lastRaisedAt", {
    id: "lastRaised",
    header: "Last raised",
    enableSorting: false,
    meta: { share: 9, min: 104 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor("numberOfRetailLocations", {
    id: "retailLocations",
    header: "Retail locations",
    enableSorting: false,
    meta: { share: 9, min: 120 },
    cell: (info) => <DataGridCell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("keywords", {
    id: "keywords",
    header: "Keywords",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={joined(info.getValue())} muted />,
  }),

  helper.accessor("technologies", {
    id: "technologies",
    header: "Technologies",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={joined(info.getValue())} muted />,
  }),

  helper.accessor("sicCodes", {
    id: "sicCodes",
    header: "SIC codes",
    enableSorting: false,
    meta: { share: 10, min: 110 },
    cell: (info) => <DataGridCell value={joined(info.getValue())} />,
  }),

  helper.accessor("naicsCodes", {
    id: "naicsCodes",
    header: "NAICS codes",
    enableSorting: false,
    meta: { share: 10, min: 120 },
    cell: (info) => <DataGridCell value={joined(info.getValue())} />,
  }),
]);

/**
 * The sortable columns, in the order the table lays them out. Each token is a column id above and the
 * server's sort token — one string, so there is no click-to-field mapping that can drift — and this
 * list is what `useGridSort` checks a remembered preference against.
 */
export const COMPANY_SORT_FIELDS = [
  "name",
  "sector",
  "country",
  "location",
  "employees",
  "revenue",
  "founded",
] as const satisfies readonly CompanySortField[];

/** Everything below Notes starts hidden: a table that opened with all 24 would be a spreadsheet. */
export const DEFAULT_COLUMN_VISIBILITY: ColumnVisibilityState = {
  location: false,
  founded: false,
  phone: false,
  state: false,
  address: false,
  parent: false,
  totalFunding: false,
  latestFunding: false,
  latestFundingAmount: false,
  lastRaised: false,
  retailLocations: false,
  keywords: false,
  technologies: false,
  sicCodes: false,
  naicsCodes: false,
};

/**
 * A scrolled row without its name is a line of anonymous figures, so the name travels with it. v9
 * names the pinned regions logically, so `start` follows the reading direction rather than the CSS.
 */
export const COLUMN_PINNING: ColumnPinningState = {
  start: ["name"],
  end: [],
};
