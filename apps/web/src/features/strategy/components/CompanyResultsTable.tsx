import {
  useTable,
  type ColumnVisibilityState,
  type OnChangeFn,
  type SortingState,
} from "@tanstack/react-table";
import { useMemo } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { CompanyResult, CompanySort, CompanySortField } from "../api/types";
import { COLUMN_PINNING, companyColumns, companyTableFeatures } from "../lib/companyColumns";

/** A stable empty array: a fresh `[]` per render invalidates every data-dependent model. */
const NO_COMPANIES: CompanyResult[] = [];

/** `gap-3` in numbers, so the row's minimum width can be added up rather than guessed. */
const ROW_GAP = 12;

/**
 * Sticky classes for a pinned column.
 *
 * <p>The hairline is a `shadow` rather than a border because a border would take part in the grid
 * track and shift every column by a pixel; a shadow paints outside the box and costs no layout. The
 * opaque background is not decoration — without it the scrolling columns show *through* the pinned
 * one, which is the classic broken-sticky-table look.
 */
const PINNED_START = "sticky start-0 ps-4 shadow-[1px_0_0_0_var(--color-line-soft)]";
const PINNED_END = "sticky end-0 pe-4 shadow-[-1px_0_0_0_var(--color-line-soft)]";

/**
 * A pinned cell has to fill its row, not just wrap its text.
 *
 * <p>The row centres its cells, so a cell is only as tall as its content. An opaque background on a
 * cell that tall is a band with daylight above and below it, and the scrolling columns slide through
 * the gaps. `self-stretch` takes the whole row height back and re-centres the content inside it.
 */
const PINNED_FILL = "flex items-center self-stretch bg-panel transition group-hover:bg-panel2";

/**
 * The company table — TanStack Table v9 driving a CSS grid.
 *
 * <p>Headless, in the strict sense: the table computes header groups, row models, visibility and
 * sort state; this file owns every pixel. It is a grid rather than a `<table>` because a `<table>`
 * cannot size its columns from state the way `grid-template-columns` can. The track list is built
 * from the *visible* leaf columns, so hiding one re-flows the header and every row together.
 *
 * <p><b>One scroll box, both axes, header sticky inside it.</b> Header and rows must never scroll
 * apart, and a body with its own `overflow-y` becomes a horizontal scroll container too — the
 * browser will not honour `overflow-x: visible` beside a scrolling y-axis — which would leave the
 * header stranded the moment the rows scrolled sideways.
 *
 * <p><b>Sorting and paging are the server's.</b> `manualSorting` is on and no sorted row model is
 * registered: this component holds one page of 25 out of tens of thousands, and sorting those 25
 * client-side would reorder the page while claiming to have ordered the result. A header click
 * therefore changes the query, not the array.
 *
 * <p><b>Sorting is single-column and cannot be cleared.</b> The API takes one field and one
 * direction, so multi-sort would silently drop everything after the first, and a third click landing
 * on "no sort" would send a request with no ORDER BY — a different, unstated ordering rather than
 * the absence of one.
 */
export function CompanyResultsTable({
  companies,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  loading,
  error,
  onAddToUniverse,
  addingId,
}: {
  companies: CompanyResult[];
  sort: CompanySort;
  onSortChange: (sort: CompanySort) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  loading: boolean;
  error: boolean;
  onAddToUniverse: (company: CompanyResult) => void;
  addingId: string | null;
}) {
  // The API's { field, direction } and the table's [{ id, desc }] are the same fact in two shapes.
  // Converting at this boundary keeps the wire shape out of the table and the table's out of the URL.
  const sorting = useMemo<SortingState>(
    () => [{ id: sort.field, desc: sort.direction === "desc" }],
    [sort],
  );

  const table = useTable({
    features: companyTableFeatures,
    columns: companyColumns,
    data: companies.length > 0 ? companies : NO_COMPANIES,
    getRowId: (company) => company.apolloAccountId,
    initialState: { columnPinning: COLUMN_PINNING },
    manualSorting: true,
    enableMultiSort: false,
    enableSortingRemoval: false,
    state: { sorting, columnVisibility },
    onSortingChange: (updater) => {
      const next = typeof updater === "function" ? updater(sorting) : updater;
      const [first] = next;
      if (!first) return;
      onSortChange({
        field: first.id as CompanySortField,
        direction: first.desc ? "desc" : "asc",
      });
    },
    onColumnVisibilityChange,
    meta: { onAddToUniverse, addingId },
  });

  const visibleColumns = table.getVisibleLeafColumns();

  /*
   * Each column's percentage share becomes an `fr`, and its floor the `minmax` minimum. `fr` rather
   * than a literal `%` because percentages resolve against the grid's content box and ignore the
   * gaps: shares summing to 100 would overflow the row by the width of every gap between them.
   * Hiding a column hands its share back to the rest, which is what a proportion should do.
   */
  const gridTemplateColumns = visibleColumns
    .map((column) => {
      const layout = column.columnDef.meta;
      if (!layout) return "1fr";
      return layout.share > 0 ? `minmax(${layout.min}px, ${layout.share}fr)` : `${layout.min}px`;
    })
    .join(" ");

  /*
   * What the row cannot shrink below: every track's floor plus the gaps between them. A grid honours
   * a `minmax` floor whether or not the container can afford it, so without this the row overflows a
   * narrow container that clips rather than scrolls, and the last column silently stops existing.
   */
  const minWidth =
    visibleColumns.reduce((total, column) => total + (column.columnDef.meta?.min ?? 96), 0) +
    (visibleColumns.length - 1) * ROW_GAP;

  const rows = table.getRowModel().rows;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[8px] border border-line bg-panel">
      <div
        role="table"
        aria-label="Companies"
        className="flex min-h-0 flex-1 flex-col overflow-auto"
      >
        {table.getHeaderGroups().map((headerGroup) => (
          <div
            key={headerGroup.id}
            role="row"
            style={{ gridTemplateColumns, minWidth }}
            className="sticky top-0 z-20 grid flex-none items-center gap-3 border-b border-line bg-panel2 py-2.5"
          >
            {headerGroup.headers.map((header) => {
              const sortable = header.column.getCanSort();
              const sorted = header.column.getIsSorted();
              const pinned = header.column.getIsPinned();
              const label = (
                // `block` for the same reason the body cells need it: an inline box ignores
                // overflow, so "EMPLOYEES" would run straight over "FOUNDED" rather than clip.
                <span
                  className={cn(
                    "block truncate font-sans text-[11px] font-semibold uppercase tracking-[0.04em]",
                    sorted ? "text-text" : "text-text3",
                    header.column.columnDef.meta?.align === "right" && "text-right",
                  )}
                >
                  <table.FlexRender header={header} />
                  {sorted && (sorted === "asc" ? " ↑" : " ↓")}
                </span>
              );
              return (
                <div
                  key={header.id}
                  role="columnheader"
                  aria-sort={sorted ? (sorted === "asc" ? "ascending" : "descending") : undefined}
                  className={cn(
                    "min-w-0",
                    // The gutter travels with the pinned cell: a sticky box stops at the scrollport
                    // edge, so padding left on the row would scroll away from underneath it.
                    pinned === "start" && `${PINNED_START} z-10 self-stretch bg-panel2`,
                    pinned === "end" && `${PINNED_END} z-10 self-stretch bg-panel2`,
                    !pinned && "first:ps-4 last:pe-4",
                  )}
                >
                  {sortable ? (
                    <button
                      type="button"
                      onClick={header.column.getToggleSortingHandler()}
                      className="block w-full text-left transition hover:opacity-80"
                    >
                      {label}
                    </button>
                  ) : (
                    label
                  )}
                </div>
              );
            })}
          </div>
        ))}

        <div className="flex-1">
          {/* A refused read is not an empty market: branch on the error before the count, or a 403
              renders "no companies match" as a fact about the data. */}
          {error ? (
            <Message>
              That list could not be loaded. Refresh, or check you still have access.
            </Message>
          ) : loading && rows.length === 0 ? (
            <RowSkeleton />
          ) : rows.length === 0 ? (
            <Message>No companies match this filter. Widen it, or reset an accordion.</Message>
          ) : (
            rows.map((row) => (
              <div
                key={row.id}
                role="row"
                style={{ gridTemplateColumns, minWidth }}
                className="group grid h-[52px] items-center gap-3 border-b border-line-soft transition hover:bg-panel2"
              >
                {row.getVisibleCells().map((cell) => {
                  const pinned = cell.column.getIsPinned();
                  return (
                    <div
                      key={cell.id}
                      role="cell"
                      className={cn(
                        "min-w-0",
                        // The row's hover tint is painted by the row, which the pinned cell covers —
                        // so the pinned cell has to repaint it or it stays white as the row lights up.
                        pinned && PINNED_FILL,
                        pinned === "start" && PINNED_START,
                        pinned === "end" && PINNED_END,
                        !pinned && "first:ps-4 last:pe-4",
                      )}
                    >
                      <table.FlexRender cell={cell} />
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <div className="px-4 py-10 text-center font-mono text-[13px] text-text3">
      <Icon d={ICONS.search} size={18} className="mx-auto mb-2 text-text3" />
      {children}
    </div>
  );
}

function RowSkeleton() {
  return (
    <div>
      {Array.from({ length: 8 }, (_, index) => (
        <div key={index} className="flex h-[52px] items-center border-b border-line-soft px-4">
          <div className="h-3 w-1/3 animate-pulse rounded bg-panel2" />
        </div>
      ))}
    </div>
  );
}
