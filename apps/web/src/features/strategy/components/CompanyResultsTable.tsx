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

// A shadow rather than a border: a border would join the grid track and shift every column by a
// pixel. The opaque background stops the scrolling columns showing through the pinned one.
const PINNED_START = "sticky start-0 ps-4 shadow-[1px_0_0_0_var(--color-line-soft)]";
const PINNED_END = "sticky end-0 pe-4 shadow-[-1px_0_0_0_var(--color-line-soft)]";

// The row centres its cells, so without `self-stretch` an opaque cell is a band with daylight
// above and below it, and the scrolling columns slide through the gaps.
const PINNED_FILL = "flex items-center self-stretch bg-panel transition group-hover:bg-panel2";

/**
 * The company table: TanStack Table v9 computing the models, this file owning every pixel.
 *
 * <p>One scroll box for both axes with the header sticky inside it. A body with its own
 * `overflow-y` becomes a horizontal scroll container too — the browser will not honour
 * `overflow-x: visible` beside a scrolling y-axis — which would strand the header the moment the
 * rows scrolled sideways.
 *
 * <p>Sorting and paging are the server's: this holds one page of 25 out of tens of thousands, so a
 * header click changes the query rather than the array. Single-column and non-clearable, because
 * the API takes one field and one direction and a third click would send no ORDER BY at all.
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
  // The API's { field, direction } and the table's [{ id, desc }] are one fact in two shapes.
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
   * `fr`, not a literal `%`: percentages resolve against the grid's content box and ignore the gaps,
   * so shares summing to 100 would overflow the row by the width of every gap between them.
   */
  const gridTemplateColumns = visibleColumns
    .map((column) => {
      const layout = column.columnDef.meta;
      if (!layout) return "1fr";
      return layout.share > 0 ? `minmax(${layout.min}px, ${layout.share}fr)` : `${layout.min}px`;
    })
    .join(" ");

  // A grid honours a `minmax` floor whatever the container's width, so without this sum the row
  // overflows a container that clips rather than scrolls, and the last column stops existing.
  const minWidth =
    visibleColumns.reduce((total, column) => total + (column.columnDef.meta?.min ?? 96), 0) +
    (visibleColumns.length - 1) * ROW_GAP;

  const rows = table.getRowModel().rows;

  const refreshing = loading && rows.length > 0;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[8px] border border-line bg-panel">
      <div
        role="table"
        aria-label="Companies"
        aria-busy={loading}
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
                // `block`: an inline box ignores overflow, so "EMPLOYEES" would run over "FOUNDED", not clip.
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
                    // The gutter travels with the pinned cell; padding on the row would scroll out from under it.
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

        <div
          className={cn(
            "flex-1 transition-opacity",
            refreshing && "pointer-events-none opacity-40",
          )}
        >
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
                        // The row paints the hover tint and the pinned cell covers it, so it has to repaint it.
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
