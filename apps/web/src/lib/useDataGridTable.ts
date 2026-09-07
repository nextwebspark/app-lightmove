import {
  useTable,
  type ColumnOrderState,
  type ColumnPinningState,
  type ColumnVisibilityState,
  type OnChangeFn,
  type PaginationState,
  type ReactTable,
  type RowData,
  type SortingState,
  type TableFeatures,
  type TableOptions,
  type Updater,
} from "@tanstack/react-table";
import { useMemo } from "react";
import type { GridLayout } from "./useGridLayout";
import type { GridSort } from "./useGridSort";

/** A stable empty array: a fresh `[]` per render invalidates every data-dependent model. */
const NO_ROWS: readonly never[] = [];

export interface DataGridTableOptions<
  TFeatures extends TableFeatures,
  TData extends RowData,
  TField extends string,
> {
  features: TFeatures;
  columns: TableOptions<TFeatures, TData>["columns"];
  data: readonly TData[];
  getRowId: (row: TData, index: number) => string;
  /** Which column travels with the row when it scrolls sideways. */
  pinning: ColumnPinningState;
  sort: GridSort<TField>;
  onSortChange: (sort: GridSort<TField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  /** What the row actions need, supplied per render. Grids whose rows only read have none. */
  meta?: TableOptions<TFeatures, TData>["meta"];
  /**
   * The page window, when this table holds every row and pages them itself. Leaving it out is the
   * statement that the server sorted and paged — the rows handed over are one page of a larger result
   * and must not be re-sorted as though they were the whole of it.
   */
  pagination?: PaginationState;
  onPaginationChange?: OnChangeFn<PaginationState>;
}

/**
 * The wiring every {@link DataGrid} needs, in one place: the sort adapter, the column-order adapter,
 * the pinned first column, and the single-column non-clearable sorting all five grids share.
 *
 * <p>Two shapes of table come out of it. Without `pagination` the sort and the page are the server's,
 * so no row model is computed here and a header click only changes the query. With it, the caller
 * handed over every row it has and the table sorts and pages them — which is what the workspace lists
 * do, where one query answers with the firm's whole roster.
 *
 * <p>Sorting is single-column and non-clearable either way: the paged APIs take one field and one
 * direction, and a third click that sent no ORDER BY at all would page over an undefined order.
 */
export function useDataGridTable<
  TFeatures extends TableFeatures,
  TData extends RowData,
  TField extends string,
>({
  features,
  columns,
  data,
  getRowId,
  pinning,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
  meta,
  pagination,
  onPaginationChange,
}: DataGridTableOptions<TFeatures, TData, TField>): ReactTable<TFeatures, TData> {
  // The API's { field, direction } and the table's [{ id, desc }] are one fact in two shapes.
  const sorting = useMemo<SortingState>(
    () => [{ id: sort.field, desc: sort.direction === "desc" }],
    [sort],
  );

  const options = {
    features,
    columns,
    data: data.length > 0 ? data : (NO_ROWS as readonly TData[]),
    getRowId,
    initialState: { columnPinning: pinning },
    manualSorting: pagination === undefined,
    enableMultiSort: false,
    enableSortingRemoval: false,
    // A filter that shrank the result is the caller's to answer for: it owns the page index, and
    // resetting it here on every new array would bounce a reader to page one on a refetch.
    autoResetPageIndex: false,
    state: { sorting, columnVisibility, columnOrder: layout.order, ...(pagination && { pagination }) },
    onSortingChange: (updater: Updater<SortingState>) => {
      const next = typeof updater === "function" ? updater(sorting) : updater;
      const [first] = next;
      if (!first) return;
      onSortChange({ field: first.id as TField, direction: first.desc ? "desc" : "asc" });
    },
    onColumnVisibilityChange,
    onColumnOrderChange: (updater: Updater<ColumnOrderState>) => {
      const order = typeof updater === "function" ? updater(layout.order) : updater;
      onLayoutChange({ ...layout, order });
    },
    onPaginationChange,
    meta,
  };

  /*
   * The one cast, for the reason DataGrid's own is there: v9 derives a table's options from the
   * feature keys present in TFeatures, and that lookup cannot resolve through a generic parameter —
   * `pagination` is a key of the state only once the caller registered the pagination feature, which
   * is a fact about the concrete features object and not about `TFeatures extends TableFeatures`.
   */
  return useTable<TFeatures, TData>(options as unknown as TableOptions<TFeatures, TData>);
}
