import {
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { CompanyResult } from "../api/types";

interface CompanyColumnMeta {
  /** Percentage of the table's flexible width. 0 pins the column at {@code min}. */
  share: number;
  /** The width the column will not shrink below, in px. */
  min: number;
  align?: "right";
}

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
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as CompanyColumnMeta,
  tableMeta: {} as CompanyTableMeta,
});

const helper = createColumnHelper<typeof companyTableFeatures, CompanyResult>();

/**
 * Each sortable column's id is its wire sort token, the same string `CompanySortField` allowlists,
 * so there is no click-to-field mapping that can drift.
 */
export const companyColumns = helper.columns([
  helper.accessor("companyName", {
    id: "name",
    header: "Company",
    enableHiding: false,
    // The floor covers a thirty-character name: logo and gutters eat 54px before a letter draws.
    meta: { share: 22, min: 280 },
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
    meta: { share: 12, min: 96 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("industry", {
    id: "sector",
    header: "Sector",
    meta: { share: 18, min: 120 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("companyCity", {
    id: "location",
    header: "City",
    meta: { share: 11, min: 96 },
    cell: (info) => <Cell value={info.getValue()} />,
  }),

  helper.accessor("annualRevenue", {
    id: "revenue",
    header: "Revenue",
    meta: { share: 11, min: 96 },
    cell: (info) => <Cell value={formatRevenue(info.getValue())} />,
  }),

  helper.accessor("numEmployees", {
    id: "employees",
    header: "Employees",
    meta: { share: 9, min: 84 },
    cell: (info) => <Cell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor("foundedYear", {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84 },
    cell: (info) => <Cell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor("shortDescription", {
    id: "notes",
    header: "Notes",
    // Not in the server's sort allowlist: alphabetising a description answers no question.
    enableSorting: false,
    meta: { share: 12, min: 130 },
    cell: (info) => <Cell value={info.getValue()} muted />,
  }),

  helper.display({
    id: "actions",
    header: "Actions",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 128, align: "right" },
    cell: (info) => {
      const company = info.row.original;
      const meta = info.table.options.meta;
      return (
        <span className="flex justify-end gap-1.5">
          <button
            type="button"
            title="Add to universe"
            aria-label={`Add ${company.companyName} to universe`}
            onClick={() => meta?.onAddToUniverse(company)}
            disabled={meta?.addingId === company.apolloAccountId}
            className="grid size-6 place-items-center rounded-[5px] text-text3 transition hover:bg-panel2 hover:text-text disabled:opacity-40"
          >
            <Icon d={ICONS.plus} size={14} />
          </button>
        </span>
      );
    },
  }),
]);

/** City and Founded are real and sortable; eight columns is the table's budget, so they start off. */
export const DEFAULT_COLUMN_VISIBILITY: ColumnVisibilityState = {
  location: false,
  founded: false,
};

/**
 * A scrolled row without its name is eight anonymous figures. v9 names the pinned regions logically,
 * so `start`/`end` follow the reading direction rather than the CSS.
 */
export const COLUMN_PINNING: ColumnPinningState = {
  start: ["name"],
  end: ["actions"],
};

function Cell({ value, muted }: { value: string | null; muted?: boolean }) {
  return (
    <TruncatedText
      value={value}
      className={muted ? "font-sans text-[13px] text-text3" : "font-sans text-[13px] text-text2"}
    />
  );
}

/** Null is the common case — nine rows in ten publish no figure — and reads as unknown, not zero. */
function formatRevenue(revenue: number | null): string {
  if (revenue === null) return "—";
  if (revenue >= 1_000_000_000) return `$${trim(revenue / 1_000_000_000)}B`;
  if (revenue >= 1_000_000) return `$${trim(revenue / 1_000_000)}M`;
  return `$${revenue.toLocaleString()}`;
}

function trim(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}
