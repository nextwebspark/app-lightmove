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
import { Icon } from "../../../components/layout/Icon";
import type { CompanyResult } from "../api/types";

/**
 * Per-column layout: a percentage of the table's width, and the pixel width it will not go below.
 *
 * <p><b>{@code share} is a percentage.</b> The eight flexible columns' shares add up to 100, so the
 * table reads as the proportions it actually renders. They are emitted as `fr` rather than `%`
 * because a grid's percentages resolve against its content box and take no account of the gaps
 * between tracks — eight columns of literal percentages summing to 100 overflow their own row by the
 * width of seven gaps, every time. `fr` divides what is *left after* the gaps, which is what the
 * percentages were meant to say in the first place.
 *
 * <p><b>{@code share: 0} means fixed at {@code min}.</b> Fit and Actions hold a dash and a button;
 * neither reads better with a share of a wide screen, and giving them one takes width from the
 * columns carrying data.
 *
 * <p><b>{@code min} is a floor, not a width.</b> Percentages alone are not responsive, they are
 * merely proportional: at 900px, 11% of the row is 99px and 5% is 45px, which turns a revenue figure
 * into an ellipsis. The floors hold every column at a legible size and let the row scroll sideways
 * instead — which is why the table adds them up (see {@code CompanyResultsTable}).
 */
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
 * Only the three features this table actually uses.
 *
 * <p>Registration is what creates the state and the APIs, so this list is the honest statement of
 * what the table does: columns can be hidden, pinned, and sorted on. <b>Nothing is registered for
 * pagination or filtering</b> — both are the server's, and a client-side row model would quietly
 * re-page or re-filter the 25 rows this page happens to hold as though they were the whole result.
 *
 * <p>There is no `sortedRowModel` for the same reason: `manualSorting` sends the sort to the API,
 * and a client-side sort of one page would reorder 25 of 71,822 companies and call it sorted.
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
 * The company table's columns.
 *
 * <p><b>Every sortable column's id is its wire sort token</b> — `name`, `sector`, `country`,
 * `location`, `employees`, `revenue`, `founded` — the same strings `CompanySortField` allowlists on
 * the server. That is deliberate: the sort travels to the API as a column id, so a mapping table
 * between "the column the user clicked" and "the field the server sorts by" cannot drift, because
 * there isn't one.
 *
 * <p>Columns with no server sort behind them (`fit`, `notes`, `actions`) set `enableSorting: false`
 * rather than rendering a header that looks clickable and does nothing.
 */
export const companyColumns = helper.columns([
  helper.accessor("companyName", {
    id: "name",
    header: "Company",
    // Pinned in place of the row identity: hiding the name would leave a row of figures about
    // nothing, and the logo has to travel with it.
    enableHiding: false,
    /*
     * The widest share, and the highest floor. "Ministry of Health Saudi Arabia" is thirty characters,
     * and the logo, its gutter and the pinned padding eat 54px before a letter is drawn — so a 196px
     * track showed about half of it and truncated most of the register. The floor covers the common
     * case outright and the largest share of the table keeps it covered as the window grows; genuine
     * outliers still clip, and hovering one shows it whole.
     */
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
    // No score behind it yet: the criteria model that produced one is gone, and every row here
    // matches every selected filter equally. It fills in when AI Research lands.
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
    // The server refuses to sort on it, and alphabetising a description answers no question.
    enableSorting: false,
    // A modest share on purpose: a paragraph is truncated at any width this table can offer, so
    // width bought here buys nothing, and the same width buys a whole company name next door.
    meta: { share: 12, min: 130 },
    // Apollo's descriptions run to a paragraph. One clipped line in a 40px row, the rest on hover:
    // the column answers "roughly what is this company" at a glance, and the profile answers the
    // rest. Wrapping it would make one row four rows tall and the ones beside it mostly whitespace.
    cell: (info) => <Cell value={info.getValue()} muted />,
  }),

  helper.display({
    id: "actions",
    header: "Actions",
    enableSorting: false,
    // The one thing this screen is for. Hiding it would leave a table you can only read.
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
            <Icon d="M12 5v14M5 12h14" size={14} />
          </button>
        </span>
      );
    },
  }),
]);

/**
 * The columns a fresh mandate opens without.
 *
 * <p>City and Founded are real Apollo fields the server can sort by, but the wireframe's table is
 * eight columns wide and adding two more would squeeze every one of them. They are one tick away in
 * the Columns menu instead, which is the whole reason that menu exists.
 */
export const DEFAULT_COLUMN_VISIBILITY: ColumnVisibilityState = {
  location: false,
  founded: false,
};

/**
 * The two columns that stay put while the rest scroll sideways.
 *
 * <p>Ten columns do not fit a laptop, so the table scrolls horizontally — and a scrolled row without
 * its company name is eight anonymous figures. The name anchors what you are reading; the add button
 * is what you came to press, and having to scroll back for it after finding a company turns one
 * decision into two. Everything between them is detail you scroll *through*, which is exactly what
 * pinning is for.
 */
export const COLUMN_PINNING: ColumnPinningState = {
  // v9 names the regions logically — `start`/`end` rather than left/right — so an RTL layout pins the
  // name where reading begins rather than where the CSS happens to put it.
  start: ["name"],
  end: ["actions"],
};

/**
 * One line of clipped text, revealed in full on hover when — and only when — it is clipped.
 *
 * <p>{@link TruncatedText} carries the `block` that makes the clipping work at all: `truncate` is
 * `overflow:hidden` plus `text-overflow:ellipsis`, and an inline box ignores `overflow`, so an inline
 * span clips nothing and its text runs straight over the next column. These spans were grid items
 * once, which blockified them and hid that; they sit inside a cell wrapper now.
 */
function Cell({ value, muted }: { value: string | null; muted?: boolean }) {
  return (
    <TruncatedText
      value={value}
      className={muted ? "font-sans text-[13px] text-text3" : "font-sans text-[13px] text-text2"}
    />
  );
}

/**
 * A revenue figure as a short band label. Null is the common case — the universe publishes one on
 * roughly a tenth of its rows — and it reads as unknown rather than as a stated zero.
 */
function formatRevenue(revenue: number | null): string {
  if (revenue === null) return "—";
  if (revenue >= 1_000_000_000) return `$${trim(revenue / 1_000_000_000)}B`;
  if (revenue >= 1_000_000) return `$${trim(revenue / 1_000_000)}M`;
  return `$${revenue.toLocaleString()}`;
}

function trim(value: number): string {
  return value >= 10 ? String(Math.round(value)) : value.toFixed(1).replace(/\.0$/, "");
}
