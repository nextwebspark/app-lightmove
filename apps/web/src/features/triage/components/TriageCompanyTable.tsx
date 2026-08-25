import {
  useTable,
  type ColumnVisibilityState,
  type OnChangeFn,
  type SortingState,
} from "@tanstack/react-table";
import { useMemo } from "react";
import { DataGrid } from "../../../components/ui/DataGrid";
import type { GridSort } from "../../../lib/useGridSort";
import type { TriageCompany, TriageCompanyStatus, TriageSortField } from "../api/types";
import {
  TRIAGE_COLUMN_PINNING,
  triageCompanyColumns,
  triageTableFeatures,
} from "../lib/triageCompanyColumns";

/** A stable empty array: a fresh `[]` per render invalidates every data-dependent model. */
const NO_COMPANIES: TriageCompany[] = [];

/**
 * The Companies half of the company grid: the mandate's own columns and its triage actions, over the
 * shared {@link DataGrid}. Everything about how the grid looks lives there, so this stage and
 * Strategy render identically without either owning a copy.
 *
 * <p>Sorting and paging are the server's, exactly as on Strategy — a header click changes the query
 * rather than the array. Single-column and non-clearable, because the API takes one field and one
 * direction and a third click would send no ORDER BY at all.
 */
export function TriageCompanyTable({
  companies,
  label,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  loading,
  error,
  emptyMessage,
  onMove,
  onDelete,
  busyId,
  canWrite,
}: {
  companies: TriageCompany[];
  label: string;
  sort: GridSort<TriageSortField>;
  onSortChange: (sort: GridSort<TriageSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  loading: boolean;
  error: boolean;
  emptyMessage: string;
  onMove: (company: TriageCompany, status: TriageCompanyStatus) => void;
  onDelete: (company: TriageCompany) => void;
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
    data: companies.length > 0 ? companies : NO_COMPANIES,
    getRowId: (company) => company.id,
    initialState: { columnPinning: TRIAGE_COLUMN_PINNING },
    manualSorting: true,
    enableMultiSort: false,
    enableSortingRemoval: false,
    state: { sorting, columnVisibility },
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
    meta: { onMove, onDelete, busyId, canWrite },
  });

  return (
    <DataGrid
      table={table}
      label={label}
      loading={loading}
      error={error}
      errorMessage="That list could not be loaded. Refresh, or check you still have access to this mandate."
      emptyMessage={emptyMessage}
    />
  );
}
