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
 * Strategy's half of the company grid: the market's columns and its one row action, over the shared
 * {@link DataGrid}. Everything about how the grid *looks* — the sticky header, the pinned Company
 * column, the scroll behaviour — lives there, so the Companies screens render identically without
 * either side owning a copy.
 *
 * <p>Every row carries a tick box in front of its name, and the header a select-all over the page —
 * both handed to {@link DataGrid} as its leading slot, so they ride the pinned column and stay on
 * screen when the row scrolls sideways. The selection itself is the table's `rowSelectionFeature`:
 * keyed by company id, so it survives the page turn that replaces every row object, and carrying the
 * shift-click anchor so a range does not have to be tracked here.
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
  rowSelection,
  onRowSelectionChange,
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
  /**
   * Which rows are ticked, keyed by company id. Held by the page rather than by this component,
   * because the bulk bar it floats over the grid acts on the selection and outlives any one page of
   * results.
   */
  rowSelection: RowSelectionState;
  onRowSelectionChange: OnChangeFn<RowSelectionState>;
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
    meta: { onAddToUniverse, addingId },
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
