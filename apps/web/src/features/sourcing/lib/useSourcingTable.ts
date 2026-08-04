import type { SortingState, VisibilityState } from "@tanstack/react-table";
import { useTable } from "@tanstack/react-table";
import type { CompanyResult } from "../api/types";
import { sourcingColumns, sourcingTableFeatures } from "../components/columns";

export type SourcingTable = ReturnType<typeof useSourcingTable>;

/** Company stays readable while the rest of a wide table scrolls under it. */
const COLUMN_PINNING = { start: ["name"], end: [] };

/** A stable empty page: a fresh `[]` each render would invalidate the table's data-derived models. */
const NO_COMPANIES: CompanyResult[] = [];

/**
 * The table instance, built by the page rather than inside `CompanyTable` because the column picker
 * lives in the toolbar and has to keep working on the empty states, where no table is rendered.
 */
export function useSourcingTable({
  companies,
  isReloading,
  sorting,
  onSortingChange,
  visibility,
  onVisibilityChange,
}: {
  companies: CompanyResult[];
  isReloading: boolean;
  sorting: SortingState;
  onSortingChange: (updater: SortingState | ((old: SortingState) => SortingState)) => void;
  visibility: VisibilityState;
  onVisibilityChange: (updater: VisibilityState | ((old: VisibilityState) => VisibilityState)) => void;
}) {
  return useTable({
    features: sourcingTableFeatures,
    columns: sourcingColumns,
    data: isReloading ? NO_COMPANIES : companies,
    state: { sorting, columnVisibility: visibility, columnPinning: COLUMN_PINNING },
    onSortingChange,
    onColumnVisibilityChange: onVisibilityChange,
    // The server returns the page already ordered; sorting it again here would silently disagree.
    manualSorting: true,
    enableSortingRemoval: true,
  });
}
