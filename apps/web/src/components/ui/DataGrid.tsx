import {
  columnPinningFeature,
  columnVisibilityFeature,
  rowSortingFeature,
  type ReactTable,
  type RowData,
  type TableFeatures,
} from "@tanstack/react-table";
import type { ReactNode } from "react";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";
import { TruncatedText } from "./TruncatedText";

/**
 * How a column claims horizontal space. `share` is a slice of the table's flexible width and `min` the
 * width it will not shrink below; `share: 0` pins the column at `min`. Declared per column in
 * `columnDef.meta`, and read here to build one grid template for the header and every row.
 */
export interface DataGridColumnLayout {
  share: number;
  min: number;
}

/** `gap-3` in numbers, so the row's minimum width can be added up rather than guessed. */
const ROW_GAP = 12;

// A shadow rather than a border: a border would join the grid track and shift every column by a
// pixel. The opaque background stops the scrolling columns showing through the pinned one.
const PINNED_START = "sticky start-0 ps-4 shadow-[1px_0_0_0_var(--color-line-soft)]";

// The row centres its cells, so without `self-stretch` an opaque cell is a band with daylight
// above and below it, and the scrolling columns slide through the gaps.
const PINNED_FILL = "flex items-center self-stretch bg-panel transition group-hover:bg-panel2";

/**
 * The company grid: TanStack Table v9 computing the models, this file owning every pixel.
 *
 * <p>Shared rather than per-screen, because Strategy and the three Companies stages are one table
 * rendering four sources. Each caller builds its own table — its own columns, its own row actions,
 * its own data — and hands the instance here; the layout, the pinning, the scroll behaviour and the
 * loading and failure states are this component's, once.
 *
 * <p>One scroll box for both axes with the header sticky inside it. A body with its own
 * `overflow-y` becomes a horizontal scroll container too — the browser will not honour
 * `overflow-x: visible` beside a scrolling y-axis — which would strand the header the moment the
 * rows scrolled sideways.
 *
 * <p>Sorting and paging belong to the caller and, in both current callers, to the server: a page
 * holds 25 rows out of tens of thousands, so a header click changes the query rather than the array.
 */
/**
 * The features every grid using this component registers, and the column meta they all declare.
 *
 * <p>It exists as a *concrete* type because v9 derives a table's API from the feature keys present in
 * `TFeatures`, and that lookup cannot resolve through a generic parameter — written against
 * `TFeatures extends TableFeatures`, this file would find that `getIsPinned`, `getIsSorted` and
 * `FlexRender` do not exist on the table it was handed. So the prop stays precisely typed for
 * callers, and the body works against this shape instead.
 */
type GridFeatures = {
  columnPinningFeature: typeof columnPinningFeature;
  columnVisibilityFeature: typeof columnVisibilityFeature;
  rowSortingFeature: typeof rowSortingFeature;
  columnMeta: DataGridColumnLayout;
};

export function DataGrid<TFeatures extends TableFeatures, TData extends RowData>({
  table,
  label,
  loading,
  error,
  errorMessage,
  emptyMessage,
}: {
  table: ReactTable<TFeatures, TData>;
  /** Names the grid for screen readers — "Companies", "Shortlisted companies". */
  label: string;
  loading: boolean;
  error: boolean;
  errorMessage: ReactNode;
  emptyMessage: ReactNode;
}) {
  /*
   * The one cast, and the reason GridFeatures exists. Every caller registers exactly those three
   * features and differs only in its `tableMeta` — which this component never reads, and which is
   * what stops two concrete table types being assignable to one another directly.
   */
  const grid = table as unknown as ReactTable<GridFeatures, TData>;
  const visibleColumns = grid.getVisibleLeafColumns();

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

  const rows = grid.getRowModel().rows;

  const refreshing = loading && rows.length > 0;

  return (
    <div className="flex min-h-0 flex-1 flex-col overflow-hidden rounded-[8px] border border-line bg-panel">
      <div
        role="table"
        aria-label={label}
        aria-busy={loading}
        className="flex min-h-0 flex-1 flex-col overflow-auto"
      >
        {grid.getHeaderGroups().map((headerGroup) => (
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
                  )}
                >
                  <grid.FlexRender header={header} />
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
          {/* A refused read is not an empty result: branch on the error before the count, or a 403
              renders "nothing here" as a fact about the data. */}
          {error ? (
            <GridMessage>{errorMessage}</GridMessage>
          ) : loading && rows.length === 0 ? (
            <RowSkeleton />
          ) : rows.length === 0 ? (
            <GridMessage>{emptyMessage}</GridMessage>
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
                        pinned === "start" && `${PINNED_FILL} ${PINNED_START}`,
                        !pinned && "first:ps-4 last:pe-4",
                      )}
                    >
                      <grid.FlexRender cell={cell} />
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

/** One ordinary text cell. Shared so a column reads the same in whichever grid it appears. */
export function DataGridCell({ value, muted }: { value: string | null; muted?: boolean }) {
  return (
    <TruncatedText
      value={value}
      className={muted ? "font-sans text-[13px] text-text3" : "font-sans text-[13px] text-text2"}
    />
  );
}

/** The icon button every grid's row actions and links are built from. */
export const GRID_ICON_BUTTON =
  "grid size-9 place-items-center rounded-[5px] text-text3 transition hover:bg-panel2 hover:text-text lg:size-6";

function GridMessage({ children }: { children: ReactNode }) {
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
