import type {
  ColumnVisibilityState,
  OnChangeFn,
  RowSelectionState,
} from "@tanstack/react-table";
import { DataGrid } from "../../../components/ui/DataGrid";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { CompanyResult, CompanySort, CompanySortField } from "../api/types";
import { COLUMN_PINNING, companyColumns, companyTableFeatures } from "../lib/companyColumns";

/**
 * Strategy's company grid over the shared {@link DataGrid}. Tick boxes ride the pinned column; selection is keyed by
 * company id so it survives a page turn. Sorting and paging are the server's, hence no `pagination` on the table.
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
  rowSelection,
  onRowSelectionChange,
  onOpenCompany,
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
  /**
   * Which rows are ticked, keyed by company id. Held by the page rather than by this component,
   * because the bulk bar it floats over the grid acts on the selection and outlives any one page of
   * results.
   */
  rowSelection: RowSelectionState;
  onRowSelectionChange: OnChangeFn<RowSelectionState>;
  /** Opens the market panel on a row. The tick box and the link icons keep their own clicks —
      {@link DataGrid} excludes every nested control from the row's. */
  onOpenCompany: (company: CompanyResult) => void;
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
    rowSelection,
    onRowSelectionChange,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
  });

  /*
   * `getIsAllPageRowsSelected` is the feature's own answer to this, but it reads the *paginated* row
   * model — and paging here is the server's, so no pagination feature is registered to build one.
   * The rows this table holds are the page.
   */
  const pageRows = table.getRowModel().rows;
  const allOnPageSelected = pageRows.length > 0 && pageRows.every((row) => row.getIsSelected());

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
      onRowClick={onOpenCompany}
      headerLead={
        <SelectionCheckbox
          checked={allOnPageSelected}
          indeterminate={!allOnPageSelected && table.getIsSomePageRowsSelected()}
          label="Select all companies on this page"
          onChange={table.getToggleAllPageRowsSelectedHandler()}
        />
      }
      rowLead={(company) => {
        const row = table.getRow(company.apolloAccountId);
        return (
          <SelectionCheckbox
            checked={row.getIsSelected()}
            label={`Select ${company.companyName}`}
            onChange={row.getToggleSelectedHandler()}
          />
        );
      }}
    />
  );
}
