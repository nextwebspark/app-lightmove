import {
  useTable,
  type ColumnVisibilityState,
  type OnChangeFn,
  type SortingState,
} from "@tanstack/react-table";
import { useMemo } from "react";
import { DataGrid } from "../../../components/ui/DataGrid";
import type { CompanyResult, CompanySort, CompanySortField } from "../api/types";
import { COLUMN_PINNING, companyColumns, companyTableFeatures } from "../lib/companyColumns";

/** A stable empty array: a fresh `[]` per render invalidates every data-dependent model. */
const NO_COMPANIES: CompanyResult[] = [];

/**
 * Strategy's half of the company grid: the market's columns and its one row action, over the shared
 * {@link DataGrid}. Everything about how the grid *looks* — the sticky header, the pinned Company
 * column, the scroll behaviour — lives there, so the Companies screens render identically without
 * either side owning a copy.
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

  return (
    <DataGrid
      table={table}
      label="Companies"
      loading={loading}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access."
      emptyMessage="No companies match this filter. Widen it, or reset an accordion."
    />
  );
}
