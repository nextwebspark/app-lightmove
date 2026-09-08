import type { ColumnVisibilityState, OnChangeFn } from "@tanstack/react-table";
import { useMemo } from "react";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Candidate } from "../../candidates/api/types";
import type { CustomColumn } from "../../customcolumns/api/types";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import {
  createTriageCompanyColumns,
  TRIAGE_COLUMN_PINNING,
  triageTableFeatures,
} from "../lib/triageCompanyColumns";
import { triageRowId, type TriageCompanyRow } from "../lib/triageRows";

/**
 * The Companies half of the company grid: the mandate's own columns and its triage actions, over the
 * shared {@link DataGrid}. Everything about how the grid looks lives there, so this stage and
 * Strategy render identically without either owning a copy.
 *
 * <p>A row is a person at a company rather than a company — see {@link TriageCompanyRow} — so the page
 * hands this the expanded lines and the grid never has to know how they were paired up.
 *
 * <p>Sorting and paging are the server's, exactly as on Strategy — a header click changes the query
 * rather than the array.
 */
export function TriageCompanyTable({
  rows,
  label,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  customColumns,
  layout,
  onLayoutChange,
  loading,
  error,
  emptyMessage,
  projectId,
  onMove,
  onDelete,
  onAddExecutive,
  onEditCandidate,
  onOpenCompany,
  busyId,
  canWrite,
}: {
  rows: TriageCompanyRow[];
  label: string;
  sort: GridSort<TriageSortField>;
  onSortChange: (sort: GridSort<TriageSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  /** This mandate's own extra columns, appended after the built-ins. */
  customColumns: readonly CustomColumn[];
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  loading: boolean;
  error: boolean;
  emptyMessage: string;
  projectId: string;
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
  onAddExecutive: (company: TriageCompany) => void;
  onEditCandidate: (candidate: Candidate) => void;
  onOpenCompany: (company: TriageCompany) => void;
  busyId: string | null;
  canWrite: boolean;
}) {
  // Rebuilt only when the project's column set changes: a fresh array every render would rebuild
  // every column def and lose the grid's own per-column state with it.
  const columns = useMemo(() => createTriageCompanyColumns(customColumns), [customColumns]);

  const table = useDataGridTable<typeof triageTableFeatures, TriageCompanyRow, TriageSortField>({
    features: triageTableFeatures,
    columns,
    data: rows,
    getRowId: triageRowId,
    pinning: TRIAGE_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    meta: {
      projectId,
      onMove,
      onDelete,
      onAddExecutive,
      onEditCandidate,
      onOpenCompany,
      busyId,
      canWrite,
    },
  });

  return (
    <DataGrid
      table={table}
      label={label}
      layout={layout}
      onLayoutChange={onLayoutChange}
      loading={loading}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access to this mandate."
      emptyMessage={emptyMessage}
    />
  );
}
