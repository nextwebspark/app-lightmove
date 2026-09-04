import {
  useTable,
  type ColumnOrderState,
  type ColumnVisibilityState,
  type OnChangeFn,
  type SortingState,
  type Updater,
} from "@tanstack/react-table";
import { useMemo } from "react";
import { DataGrid } from "../../../components/ui/DataGrid";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import type { Candidate } from "../../candidates/api/types";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import {
  TRIAGE_COLUMN_PINNING,
  triageCompanyColumns,
  triageTableFeatures,
} from "../lib/triageCompanyColumns";
import { triageRowId, type TriageCompanyRow } from "../lib/triageRows";

/** A stable empty array: a fresh `[]` per render invalidates every data-dependent model. */
const NO_ROWS: TriageCompanyRow[] = [];

/**
 * The Companies half of the company grid: the mandate's own columns and its triage actions, over the
 * shared {@link DataGrid}. Everything about how the grid looks lives there, so this stage and
 * Strategy render identically without either owning a copy.
 *
 * <p>A row is a person at a company rather than a company — see {@link TriageCompanyRow} — so the page
 * hands this the expanded lines and the grid never has to know how they were paired up.
 *
 * <p>Sorting and paging are the server's, exactly as on Strategy — a header click changes the query
 * rather than the array. Single-column and non-clearable, because the API takes one field and one
 * direction and a third click would send no ORDER BY at all.
 */
export function TriageCompanyTable({
  rows,
  label,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
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
  // The API's { field, direction } and the table's [{ id, desc }] are one fact in two shapes.
  const sorting = useMemo<SortingState>(
    () => [{ id: sort.field, desc: sort.direction === "desc" }],
    [sort],
  );

  const table = useTable({
    features: triageTableFeatures,
    columns: triageCompanyColumns,
    data: rows.length > 0 ? rows : NO_ROWS,
    getRowId: triageRowId,
    initialState: { columnPinning: TRIAGE_COLUMN_PINNING },
    manualSorting: true,
    enableMultiSort: false,
    enableSortingRemoval: false,
    state: { sorting, columnVisibility, columnOrder: layout.order },
    onSortingChange: (updater) => {
      const next = typeof updater === "function" ? updater(sorting) : updater;
      const [first] = next;
      if (!first) return;
      onSortChange({
        field: first.id as TriageSortField,
        direction: first.desc ? "desc" : "asc",
      });
    },
    onColumnVisibilityChange,
    onColumnOrderChange: (updater: Updater<ColumnOrderState>) => {
      const order = typeof updater === "function" ? updater(layout.order) : updater;
      onLayoutChange({ ...layout, order });
    },
    meta: { projectId, onMove, onDelete, onAddExecutive, onEditCandidate, onOpenCompany, busyId, canWrite },
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
