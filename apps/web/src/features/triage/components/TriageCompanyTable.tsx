import type { ColumnVisibilityState, OnChangeFn } from "@tanstack/react-table";
import { useMemo } from "react";
import { DataGrid, type DataGridColumnFilter } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Candidate, CandidateStatus } from "../../candidates/api/types";
import type { CustomColumn } from "../../customcolumns/api/types";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import {
  createTriageCompanyColumns,
  customColumnId,
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
  onSaveNote,
  onChangeCandidateStatus,
  onEditCandidate,
  onRemoveCandidate,
  onOpenCompany,
  busyIds,
  canWrite,
  onEditColumn,
  columnFilters,
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
  /** Saves the grid's own inline-edited Note cell — the same write the Companies panel makes. */
  onSaveNote: (company: TriageCompany, note: string) => Promise<unknown>;
  /** Changes a mapped executive's status inline, from the grid's own Status column. */
  onChangeCandidateStatus: (candidate: Candidate, status: CandidateStatus) => void;
  onEditCandidate: (candidate: Candidate) => void;
  onRemoveCandidate: (candidate: Candidate) => void;
  onOpenCompany: (company: TriageCompany) => void;
  busyIds: ReadonlySet<string>;
  canWrite: boolean;
  /** Opens a mandate's own column for rename, from its header menu — the real `CustomColumn` id. */
  onEditColumn?: (customColumnId: string) => void;
  /** The grid's own Company and Executive header filters, keyed by their built-in column ids. */
  columnFilters?: Record<string, DataGridColumnFilter>;
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
      onSaveNote,
      onChangeCandidateStatus,
      onEditCandidate,
      onRemoveCandidate,
      onOpenCompany,
      busyIds,
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
      columnFilters={columnFilters}
      onEditColumn={
        onEditColumn &&
        ((gridColumnId: string) => {
          const column = customColumns.find((entry) => customColumnId(entry) === gridColumnId);
          if (column) onEditColumn(column.id);
        })
      }
    />
  );
}
