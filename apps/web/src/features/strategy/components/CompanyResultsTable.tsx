import type { ColumnVisibilityState, OnChangeFn } from "@tanstack/react-table";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { CompanyResult, CompanySort, CompanySortField } from "../api/types";
import { COLUMN_PINNING, companyColumns, companyTableFeatures } from "../lib/companyColumns";

/**
 * Strategy's half of the company grid: the market's columns and its one row action, over the shared
 * {@link DataGrid}. Everything about how the grid *looks* — the sticky header, the pinned Company
 * column, the scroll behaviour — lives there, so the Companies screens render identically without
 * either side owning a copy.
 *
 * <p>Sorting and paging are the server's: this holds one page out of tens of thousands, so a
 * header click changes the query rather than the array. That is what leaving `pagination` off
 * {@link useDataGridTable} says.
 */
export function CompanyResultsTable({
  companies,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
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
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  loading: boolean;
  error: boolean;
  onAddToUniverse: (company: CompanyResult) => void;
  addingId: string | null;
}) {
  const table = useDataGridTable<typeof companyTableFeatures, CompanyResult, CompanySortField>({
    features: companyTableFeatures,
    columns: companyColumns,
    data: companies,
    getRowId: (company) => company.apolloAccountId,
    pinning: COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    meta: { onAddToUniverse, addingId },
  });

  return (
    <DataGrid
      table={table}
      label="Companies"
      layout={layout}
      onLayoutChange={onLayoutChange}
      loading={loading}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access."
      emptyMessage="No companies match this filter. Widen it, or reset an accordion."
    />
  );
}
