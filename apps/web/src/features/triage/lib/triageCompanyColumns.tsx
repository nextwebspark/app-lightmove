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
import type { Candidate } from "../../candidates/api/types";
import type { CustomColumn } from "../../customcolumns/api/types";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import { MOVES, SOURCE_STYLES } from "./triageVocabulary";
import type { TriageCompanyRow } from "./triageRows";

/**
 * What the Actions and Executive columns need, supplied per render rather than baked into the column
 * defs — the page owns the mutations, the columns only say which moves exist.
 */
interface TriageTableMeta {
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
  /** Opens the drawer to map someone new at this company. */
  onAddExecutive: (company: TriageCompany) => void;
  /** Opens the drawer on an executive already mapped. */
  onEditCandidate: (candidate: Candidate) => void;
  /** Opens the company's own panel — read-only, whatever the reader is allowed to do to it. */
  onOpenCompany: (company: TriageCompany) => void;
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
  columnOrderingFeature,
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  columnMeta: {} as DataGridColumnLayout,
  tableMeta: {} as TriageTableMeta,
});

const helper = createColumnHelper<typeof triageTableFeatures, TriageCompanyRow>();

const PILL =
  "inline-flex items-center rounded-[4px] px-[6px] py-[2px] font-mono text-[10px] font-bold uppercase tracking-[0.04em]";

/**
 * The Companies grid's columns — the same widths, shapes and sort tokens as Strategy's, so moving
 * between the market and what a mandate took from it does not feel like moving between two products.
 *
 * <p>Each sortable column's id is its wire sort token, the same string `TriageSortField` allowlists,
 * so there is no click-to-field mapping that can drift.
 *
 * <p><b>A row is a person at a company, not a company.</b> Executive, Title and Status are the mandate's
 * mapping of who sits there, and a company with three of them is three lines with the company
 * repeated. None of the three sorts: they are not in the server's allowlist, and could not be — the
 * grid is paged by company, so ordering by a person would order a page rather than a result.
 *
 * <p>Two columns Strategy has are deliberately absent: `Fit`, which has no score to show, and the
 * Facebook and X links, which the snapshot does not carry. One is added — `Source`, last in the order
 * and off by default, because a headcount from Apollo and one typed in by hand are not equally
 * trustworthy and a reader questioning a figure should be able to tell which they are looking at.
 */
const BUILT_IN_COLUMNS = helper.columns([
  helper.accessor((row) => row.company?.companyName ?? row.candidate?.companyName ?? null, {
    id: "name",
    header: "Company",
    enableHiding: false,
    // The floor covers a twenty-odd-character name: logo and gutters eat 54px before a letter draws.
    meta: { share: 22, min: 230 },
    cell: (info) => {
      const { company, position } = info.row.original;
      const name = info.getValue();

      // An executive whose employer is not in this mandate's universe. The name is what the
      // researcher typed rather than a company this screen can act on, so it reads as a caption.
      if (!company) {
        return (
          <span className="flex min-w-0 flex-col justify-center">
            <TruncatedText
              value={name ?? "No employer named"}
              className="font-sans text-[13px] text-text2"
            />
            <span className="font-mono text-[10px] uppercase tracking-[0.04em] text-text3">
              Not in universe
            </span>
          </span>
        );
      }

      const open = () => info.table.options.meta?.onOpenCompany(company);

      // The second and third executive at one company: the company is repeated because each person
      // is their own row, but repeating the logo as well reads as three separate companies.
      if (position > 1) {
        return (
          <button
            type="button"
            onClick={open}
            title={`Open ${company.companyName}`}
            aria-label={`Open ${company.companyName}`}
            className="flex min-w-0 items-center gap-2.5 ps-[38px] text-start transition hover:text-sky"
          >
            <TruncatedText value={name} className="font-sans text-[13px] text-text3" />
          </button>
        );
      }

      return (
        <button
          type="button"
          onClick={open}
          title={`Open ${company.companyName}`}
          aria-label={`Open ${company.companyName}`}
          className="flex min-w-0 items-center gap-2.5 text-start"
        >
          <CompanyLogo name={company.companyName} logo={company.logoUrl} size={28} />
          <TruncatedText
            value={name}
            className="font-sans text-[13px] font-medium text-text transition group-hover:text-sky"
          />
        </button>
      );
    },
  }),

  helper.display({
    id: "actions",
    header: "Actions",
    enableSorting: false,
    enableHiding: false,
    meta: { share: 0, min: 134 },
    cell: (info) => {
      const { company } = info.row.original;
      const meta = info.table.options.meta;
      if (!meta?.canWrite || !company) {
        return <span className="font-sans text-[13px] text-text3">—</span>;
      }
      const busy = meta.busyId === company.id;
      return (
        <span className="flex justify-start gap-1.5">
          <button
            type="button"
            title="Add an executive here"
            aria-label={`Add an executive at ${company.companyName}`}
            onClick={() => meta.onAddExecutive(company)}
            className={GRID_ICON_BUTTON}
          >
            <Icon d={ICONS.userPlus} size={14} />
          </button>
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
      const { company } = info.row.original;
      if (!company) return <span className="font-sans text-[13px] text-text3">—</span>;
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

  helper.accessor((row) => row.company?.companyCountry ?? null, {
    id: "country",
    header: "Country",
    meta: { share: 12, min: 82 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.fullName ?? null, {
    id: "executive",
    header: "Executive",
    // Not in the server's sort allowlist, and could not be: the grid is paged by company, so sorting
    // by a person would order one page rather than the mandate.
    enableSorting: false,
    meta: { share: 14, min: 150 },
    cell: (info) => {
      const { company, candidate } = info.row.original;
      const meta = info.table.options.meta;

      if (candidate) {
        return (
          <button
            type="button"
            onClick={() => meta?.onEditCandidate(candidate)}
            title={meta?.canWrite ? "Open this profile" : "View this profile"}
            className="flex min-w-0 items-center rounded-[4px] text-start font-sans text-[13px] font-medium text-text transition hover:text-sky"
          >
            <TruncatedText value={candidate.fullName} />
          </button>
        );
      }
      // A company with nobody mapped is the most useful thing this grid shows, so the empty cell is
      // the invitation rather than a dash.
      if (company && meta?.canWrite) {
        return (
          <button
            type="button"
            onClick={() => meta.onAddExecutive(company)}
            className="rounded-[4px] font-sans text-[13px] text-amber transition hover:underline"
          >
            + Add executive
          </button>
        );
      }
      return <DataGridCell value={null} />;
    },
  }),

  helper.accessor((row) => row.candidate?.title ?? null, {
    id: "executiveTitle",
    header: "Title",
    enableSorting: false,
    meta: { share: 14, min: 130 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.candidate?.status ?? null, {
    id: "executiveStatus",
    header: "Status",
    enableSorting: false,
    meta: { share: 0, min: 104 },
    cell: (info) => {
      const status = info.getValue();
      if (!status) return <DataGridCell value={null} />;
      const { label, className } = candidateStatusStyle(status);
      return <span className={`${PILL} ${className}`}>{label}</span>;
    },
  }),

  helper.accessor((row) => row.company?.industry ?? null, {
    id: "sector",
    header: "Sector",
    meta: { share: 18, min: 100 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.company?.companyCity ?? null, {
    id: "location",
    header: "City",
    meta: { share: 11, min: 82 },
    cell: (info) => <DataGridCell value={info.getValue()} />,
  }),

  helper.accessor((row) => row.company?.annualRevenue ?? null, {
    id: "revenue",
    header: "Revenue",
    meta: { share: 11, min: 82 },
    cell: (info) => <DataGridCell value={formatMoney(info.getValue())} />,
  }),

  helper.accessor((row) => row.company?.numEmployees ?? null, {
    id: "employees",
    header: "Employees",
    meta: { share: 9, min: 74 },
    cell: (info) => <DataGridCell value={info.getValue()?.toLocaleString() ?? null} />,
  }),

  helper.accessor((row) => row.company?.note ?? null, {
    id: "note",
    header: "Note",
    // Not in the server's sort allowlist: alphabetising a remark answers no question.
    enableSorting: false,
    meta: { share: 16, min: 120 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor((row) => row.company?.foundedYear ?? null, {
    id: "founded",
    header: "Founded",
    meta: { share: 5, min: 84 },
    cell: (info) => <DataGridCell value={info.getValue()?.toString() ?? null} />,
  }),

  helper.accessor((row) => row.company?.addedAt ?? row.candidate?.addedAt ?? null, {
    id: "added",
    header: "Added",
    meta: { share: 8, min: 96 },
    cell: (info) => <DataGridCell value={formatInstantDate(info.getValue())} />,
  }),

  helper.accessor((row) => row.company?.shortDescription ?? null, {
    id: "description",
    header: "Description",
    enableSorting: false,
    meta: { share: 18, min: 160 },
    cell: (info) => <DataGridCell value={info.getValue()} muted />,
  }),

  helper.accessor((row) => row.company?.source ?? null, {
    id: "source",
    header: "Source",
    enableSorting: false,
    meta: { share: 0, min: 84 },
    cell: (info) => {
      const source = info.getValue();
      if (!source) return <DataGridCell value={null} />;
      const { label, className } = SOURCE_STYLES[source];
      return <span className={`${PILL} ${className}`}>{label}</span>;
    },
  }),
]);

/**
 * The grid's columns for one mandate: the built-ins above, then a column per custom column the
 * project has defined.
 *
 * <p>A factory rather than a constant, because the column set is per project — a mandate that
 * imported a file carrying Ethnicity has a column the mandate next door does not. A parallel column
 * list for the custom ones would have to re-implement widths, pinning, ordering and the picker; one
 * list of TanStack columns gets all of that for free, and a custom column behaves exactly like a
 * built-in because it is one.
 *
 * <p>Ids are prefixed `custom:` so a project's "Note" or "Country" column can never collide with the
 * built-in of that name, whose id is the server's own sort token.
 */
export function createTriageCompanyColumns(customColumns: readonly CustomColumn[]) {
  const custom = customColumns
    .filter((column) => !column.hidden)
    .map((column) =>
      helper.accessor((row) => customValueOf(row, column), {
        id: customColumnId(column),
        header: column.label,
        // Never: sorting is the server's, and its allowlist is the built-in columns. A header that
        // looked clickable and re-ordered nothing would be worse than one that does not.
        enableSorting: false,
        meta: { share: 0, min: 120 },
        cell: (info) => <DataGridCell value={info.getValue()} />,
      }),
    );
  // Back through `helper.columns` rather than a bare spread: it is what re-widens the tuple of
  // per-column value types into the one array shape `useTable` takes, which a spread loses.
  return helper.columns([...BUILT_IN_COLUMNS, ...custom]);
}

/** The grid id for a custom column. Prefixed so it cannot collide with a built-in's id. */
export function customColumnId(column: CustomColumn): string {
  return `custom:${column.target}:${column.fieldKey}`;
}

/**
 * A custom value off whichever half of the row the column belongs to.
 *
 * <p>A company column repeats down the company's people, the way the company's own name does — the
 * fact is about the employer, and blanking it on the second and third rows would read as missing
 * data rather than as a repeat.
 */
function customValueOf(row: TriageCompanyRow, column: CustomColumn): string | null {
  const values = column.target === "company" ? row.company?.customFields : row.candidate?.customFields;
  return values?.[column.fieldKey] ?? null;
}

/**
 * What a project's grid shows by default: the built-in defaults below, plus every custom column
 * visible. A column somebody deliberately added to their own grid should not need finding in a picker.
 *
 * <p>`useColumnVisibility` merges a stored record over this, so a column added after that record was
 * written takes the default here rather than being read as hidden.
 */
export function defaultTriageColumnVisibility(
  customColumns: readonly CustomColumn[],
): ColumnVisibilityState {
  const visibility: ColumnVisibilityState = { ...DEFAULT_TRIAGE_COLUMN_VISIBILITY };
  for (const column of customColumns) {
    visibility[customColumnId(column)] = !column.hidden;
  }
  return visibility;
}

/**
 * Founded and Description start hidden, matching Strategy: a grid that opened with every column would
 * be a spreadsheet. The rest stay visible. Note, because it is the one column here the market cannot
 * supply and the reason a consultant opens this screen rather than that one; Added, because the grid
 * opens sorted by it, and a default ordering whose column is hidden shows its sort indicator nowhere;
 * and the three executive columns, because who sits in the seat is what a talent map is for.
 */
export const DEFAULT_TRIAGE_COLUMN_VISIBILITY: ColumnVisibilityState = {
  founded: false,
  description: false,
  source: false,
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
