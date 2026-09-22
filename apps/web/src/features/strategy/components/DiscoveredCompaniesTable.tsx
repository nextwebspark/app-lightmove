import type { OnChangeFn, RowSelectionState } from "@tanstack/react-table";
import { DataGrid } from "../../../components/ui/DataGrid";
import { SelectionCheckbox } from "../../../components/ui/SelectionCheckbox";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { DiscoveredCompany } from "../api/types";
import {
  DISCOVERED_COLUMN_PINNING,
  discoveredColumns,
  discoveredTableFeatures,
} from "../lib/discoveredColumns";

/**
 * An AI Research answer, in the frame the market grid was using a moment ago — same
 * {@link DataGrid}, same pinned Company column, same tick boxes under the same floating bar. The
 * consultant sees one grid whose contents changed.
 *
 * <p>Rows key on `ref` rather than an Apollo id, which is the whole reason this is not
 * {@link CompanyResultsTable}: a company the universe does not carry has no id to key on, and half
 * an answer is usually made of those.
 *
 * <p>A row the mandate already holds cannot be ticked. Filing it again would be refused server-side
 * and counted as a skip, which is honest but reads as a failure to whoever pressed the button.
 */
export function DiscoveredCompaniesTable({
  companies,
  layout,
  onLayoutChange,
  loading,
  error,
  rowSelection,
  onRowSelectionChange,
}: {
  companies: DiscoveredCompany[];
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  loading: boolean;
  error: boolean;
  rowSelection: RowSelectionState;
  onRowSelectionChange: OnChangeFn<RowSelectionState>;
}) {
  const table = useDataGridTable<typeof discoveredTableFeatures, DiscoveredCompany, never>({
    features: discoveredTableFeatures,
    columns: discoveredColumns,
    data: companies,
    getRowId: (company) => company.ref,
    pinning: DISCOVERED_COLUMN_PINNING,
    // The answer's order is the server's, fit first. There is no sort feature to change it.
    sort: { field: "name" as never, direction: "asc" },
    onSortChange: () => {},
    columnVisibility: {},
    onColumnVisibilityChange: () => {},
    rowSelection,
    onRowSelectionChange,
    layout,
    onLayoutChange,
  });

  const fileable = table.getRowModel().rows.filter((row) => !row.original.alreadyInMandate);
  const allFileableSelected = fileable.length > 0 && fileable.every((row) => row.getIsSelected());

  return (
    <DataGrid
      table={table}
      label="Companies AI Research found"
      layout={layout}
      onLayoutChange={onLayoutChange}
      loading={loading}
      error={error}
      errorMessage="That search could not be run. Try again in a moment."
      emptyMessage="Nothing came back for that question. Try naming the market differently."
      headerLead={
        <SelectionCheckbox
          checked={allFileableSelected}
          indeterminate={!allFileableSelected && fileable.some((row) => row.getIsSelected())}
          label="Select every company that is not already added"
          onChange={() => {
            const selecting = !allFileableSelected;
            fileable.forEach((row) => row.toggleSelected(selecting));
          }}
        />
      }
      rowLead={(company) => {
        const row = table.getRow(company.ref);
        return (
          <SelectionCheckbox
            checked={row.getIsSelected()}
            disabled={company.alreadyInMandate}
            label={
              company.alreadyInMandate
                ? `${company.companyName} is already in this mandate`
                : `Select ${company.companyName}`
            }
            onChange={row.getToggleSelectedHandler()}
          />
        );
      }}
    />
  );
}
