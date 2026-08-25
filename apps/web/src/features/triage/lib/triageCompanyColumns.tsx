import {
  columnPinningFeature,
  columnVisibilityFeature,
  createColumnHelper,
  rowSortingFeature,
  tableFeatures,
  type ColumnPinningState,
  type ColumnVisibilityState,
} from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { CompanyLink } from "../../../components/ui/CompanyLink";
import { CompanyLogo } from "../../../components/ui/CompanyLogo";
import {
  DataGridCell,
  GRID_ICON_BUTTON,
  type DataGridColumnLayout,
} from "../../../components/ui/DataGrid";
import { TruncatedText } from "../../../components/ui/TruncatedText";
import { formatInstantDate, formatMoney } from "../../../lib/format";
import type { TriageCompany, TriageCompanySource, TriageCompanyStatus, TriageSortField } from "../api/types";

/**
 * What the Actions column needs, supplied per render rather than baked into the column defs — the
 * page owns the mutations, the columns only say which moves exist.
 */
interface TriageTableMeta {
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
  /** The row with a write in flight, so its actions can be disabled without freezing the grid. */
  busyId: string | null;
  /** False for a client representative, who reads these grids but moves nothing. */
  canWrite: boolean;
}

/**
 * No pagination, filtering or sorted row model registered: all three are the server's, and a client
 * row model would re-sort the 25 rows this page holds as though they were the whole result. The same
 * three features Strategy registers, because it is the same grid.
 */
export const triageTableFeatures = tableFeatures({
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as TriageTableMeta,
});

const helper = createColumnHelper<typeof triageTableFeatures, TriageCompany>();

/**
 * The move a stage offers, keyed by where the row currently is. A company is never offered the stage
 * it is already in, so every button on a row does something.
 */
const MOVES: Record<TriageCompanyStatus, { status: TriageCompanyStatus; label: string; icon: string }[]> = {
  inUniverse: [
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
    { status: "declined", label: "Decline", icon: ICONS.close },
  ],
  shortlisted: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "declined", label: "Decline", icon: ICONS.close },
  ],
  declined: [
    { status: "inUniverse", label: "Back to universe", icon: ICONS.globe },
    { status: "shortlisted", label: "Shortlist", icon: ICONS.star },
  ],
};

/** How each source reads in the grid. "Plugin" rather than "Extension" — that is what people call it. */
const SOURCE_STYLES: Record<TriageCompanySource, { label: string; className: string }> = {
  strategy: { label: "Strategy", className: "text-sky bg-sky-dim" },
  manual: { label: "Manual", className: "text-amber bg-amber-dim" },
  extension: { label: "Plugin", className: "text-green bg-green-dim" },
};

/**
 * The Companies grid's columns — the same widths, shapes and sort tokens as Strategy's, so moving
 * between the market and what a mandate took from it does not feel like moving between two products.
 *
 * <p>Each sortable column's id is its wire sort token, the same string `TriageSortField` allowlists,
 * so there is no click-to-field mapping that can drift.
 *
 * <p>Two columns Strategy has are deliberately absent: `Fit`, which has no score to show, and the
 * Facebook and X links, which the snapshot does not carry. One is added — `Source`, because a
 * headcount from Apollo and one typed in by hand are not equally trustworthy and the reader should be
 * able to tell which they are looking at.
 */
export const triageCompanyColumns = helper.columns([
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
    meta: { share: 0, min: 104 },
    cell: (info) => {
      const company = info.row.original;
      const meta = info.table.options.meta;
      if (!meta?.canWrite) {
        return <span className="font-sans text-[13px] text-text3">—</span>;
      }
      const busy = meta.busyId === company.id;
      return (
        <span className="flex justify-start gap-1.5">
          {MOVES[company.status].map((move) => (
            <button
              key={move.status}
              type="button"
              title={move.label}
              aria-label={`${move.label}: ${company.companyName}`}
              disabled={busy}
              onClick={() => meta.onMove(company, move.status)}
              className={`${GRID_ICON_BUTTON} disabled:opacity-40`}
            >
              <Icon d={move.icon} size={14} />
            </button>
          ))}
          <button
            type="button"
            title="Remove from this mandate"
            aria-label={`Remove ${company.companyName} from this mandate`}
            disabled={busy}
            onClick={() => meta.onDelete(company)}
            className={`${GRID_ICON_BUTTON} hover:text-red disabled:opacity-40`}
          >
            <Icon d={ICONS.trash} size={14} />
          </button>
        </span>
      );
    },
  }),

  helper.display({
    id: "links",
    header: "Links",
    enableSorting: false,
    meta: { share: 0, min: 56 },
    cell: (info) => {
      const company = info.row.original;
      return (
        <span className="flex justify-start gap-1">
          <CompanyLink
            url={company.website}
            icon={ICONS.globe}
            label="website"
            companyName={company.companyName}
          />
          <CompanyLink
            url={company.companyLinkedinUrl}
            icon={ICONS.linkedin}
            label="LinkedIn"
            companyName={company.companyName}
          />
        </span>
      );
    },
  }),

  helper.accessor("source", {
    id: "source",
    header: "Source",
    enableSorting: false,
    meta: { share: 0, min: 84 },
    cell: (info) => {
      const { label, className } = SOURCE_STYLES[info.getValue()];
      return (
        <span
          className={`inline-flex items-center rounded-[4px] px-[6px] py-[2px] font-mono text-[10px] font-bold uppercase tracking-[0.04em] ${className}`}
        >
          {label}
        </span>
      );
    },
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

  helper.accessor("note", {
    id: "note",
    header: "Note",
    // Not in the server's sort allowlist: alphabetising a remark answers no question.
    enableSorting: false,
    meta: { share: 16, min: 120 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor("foundedYear", {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84 },
    cell: (info) => <DataGridCell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor("addedAt", {
    id: "added",
    header: "Added",
    meta: { share: 8, min: 96 },
    cell: (info) => <DataGridCell value={formatInstantDate(info.getValue())} />,
  }),

  helper.accessor("shortDescription", {
    id: "description",
    header: "Description",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),
]);

/**
 * Founded and Description start hidden, matching Strategy: a grid that opened with every column would
 * be a spreadsheet. Two stay visible. Note, because it is the one column here the market cannot supply
 * and the reason a consultant opens this screen rather than that one — and Added, because the grid
 * opens sorted by it, and a default ordering whose column is hidden shows its sort indicator nowhere.
 */
export const DEFAULT_TRIAGE_COLUMN_VISIBILITY: ColumnVisibilityState = {
  founded: false,
  description: false,
};

/**
 * A scrolled row without its name is a line of anonymous figures, so the name travels with it. v9
 * names the pinned regions logically, so `start` follows the reading direction rather than the CSS.
 */
export const TRIAGE_COLUMN_PINNING: ColumnPinningState = {
  start: ["name"],
  end: [],
};

/** The sortable column ids, which are also the server's sort tokens. */
export const TRIAGE_SORT_FIELDS = [
  "name",
  "sector",
  "country",
  "location",
  "employees",
  "revenue",
  "founded",
  "added",
] as const satisfies readonly TriageSortField[];
