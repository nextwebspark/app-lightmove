import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { RowSelectionState } from "@tanstack/react-table";
import { useEffect, useMemo, useState, type ReactNode } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { EmptyState, FullscreenButton } from "../../../../components/ui";
import { DataGrid } from "../../../../components/ui/DataGrid";
import { PaginationBar } from "../../../../components/ui/PaginationBar";
import { SelectionAction, SelectionActionBar } from "../../../../components/ui/SelectionActionBar";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { useDebouncedValue } from "../../../../lib/useComboboxList";
import { useDataGridTable } from "../../../../lib/useDataGridTable";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../../lib/useFullscreen";
import { layoutColumnsOf, useGridLayout } from "../../../../lib/useGridLayout";
import { useGridPaging } from "../../../../lib/useGridPaging";
import * as poolApi from "../../api/poolApi";
import type { PoolFilters, PoolRow, PoolSortField } from "../../api/types";
import { POOL_COLUMN_PINNING, poolColumnsFor, poolTableFeatures } from "../../lib/poolColumns";
import { countActiveFilters } from "../../lib/poolFilters";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { AddToPositionDialog } from "./AddToPositionDialog";
import { ActiveFilters } from "./ActiveFilters";
import { PeopleToolbar } from "./PeopleToolbar";
import { PoolFilterRail } from "./PoolFilterRail";
import { SetOwnerDialog } from "./SetOwnerDialog";
import { TagPeopleDialog } from "./TagPeopleDialog";

const POOL_LAYOUT_COLUMNS = layoutColumnsOf(poolColumnsFor(new Map(), new Map()));
const NOTHING_SELECTED: RowSelectionState = {};

type BulkDialog = "position" | "tag" | "owner" | null;

/**
 * The People view of the Candidates page: everyone the workspace holds, searched, filtered and sorted
 * by the server, with the selection bar's bulk actions over the people ticked. Strategy's frame — the
 * toolbar, the filter rail beside the grid, the paging row with full screen under it.
 */
export function PeopleView({
  toggle,
  filters,
  onFiltersChange,
  onOpen,
  onExport,
  exporting,
}: {
  /** The People | Activity switch, drawn first in this view's toolbar. */
  toggle: ReactNode;
  filters: PoolFilters;
  onFiltersChange: (filters: PoolFilters) => void;
  onOpen: (personId: string) => void;
  onExport: (personIds: string[]) => void;
  exporting: boolean;
}) {
  const lookups = usePoolLookups();
  const paging = useGridPaging(50);
  const [query, setQuery] = useState(filters.q);
  // Closed at every width, unlike Strategy's: most visits here are a search or a quick view.
  const [filtersOpen, setFiltersOpen] = useState(false);
  const [isFullscreen, toggleFullscreen] = useFullscreen();
  const [rowSelection, setRowSelection] = useState<RowSelectionState>(NOTHING_SELECTED);
  const [dialog, setDialog] = useState<BulkDialog>(null);
  const [layout, setLayout] = useGridLayout("candidatePool", POOL_LAYOUT_COLUMNS);
  const { reset: resetPaging, clampTo, page: pageIndex, size: pageSize } = paging;
  const settledQuery = useDebouncedValue(query);

  useEffect(() => {
    if (settledQuery !== filters.q) onFiltersChange({ ...filters, q: settledQuery });
  }, [settledQuery, filters, onFiltersChange]);

  // The selection is what the bulk actions count and summarise, and they can only see the rows on
  // screen — so a new question, or a turned page, starts it again.
  useEffect(() => {
    resetPaging();
    setRowSelection(NOTHING_SELECTED);
  }, [filters, resetPaging]);
  useEffect(() => {
    setRowSelection(NOTHING_SELECTED);
  }, [pageIndex, pageSize]);

  const page = useQuery({
    queryKey: poolApi.POOL_PAGE_KEY(filters, paging.page, paging.size),
    queryFn: ({ signal }) => poolApi.listPool(filters, paging.page, paging.size, signal),
    placeholderData: keepPreviousData,
  });
  const rows = page.data?.people ?? [];
  const totalCount = page.data?.totalCount;
  useEffect(() => {
    if (totalCount !== undefined) clampTo(totalCount);
  }, [clampTo, totalCount]);
  const selectedIds = useMemo(() => Object.keys(rowSelection), [rowSelection]);

  const columns = useMemo(
    () => poolColumnsFor(lookups.tagsById, lookups.membersByUserId),
    [lookups.tagsById, lookups.membersByUserId],
  );
  const table = useDataGridTable<typeof poolTableFeatures, PoolRow, PoolSortField>({
    features: poolTableFeatures,
    columns,
    data: rows,
    getRowId: (row) => row.personId,
    pinning: POOL_COLUMN_PINNING,
    sort: { field: filters.sort, direction: filters.direction },
    onSortChange: (sort) => onFiltersChange({ ...filters, sort: sort.field, direction: sort.direction }),
    layout,
    onLayoutChange: setLayout,
    rowSelection,
    onRowSelectionChange: setRowSelection,
  });

  const tickedOnPage = rows.filter((row) => rowSelection[row.personId]).length;
  const selectedPeople = selectedIds.length;
  const poolIsEmpty = page.isSuccess && page.data.poolSize === 0;

  return (
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <PeopleToolbar
        toggle={toggle}
        filtersOpen={filtersOpen}
        onToggleFilters={() => setFiltersOpen((open) => !open)}
        activeFilterCount={countActiveFilters(filters)}
        query={query}
        onQueryChange={setQuery}
        view={filters.view}
        viewCounts={page.data?.viewCounts}
        onViewChange={(view) => onFiltersChange({ ...filters, view })}
        shown={page.data?.totalCount ?? null}
        pool={page.data?.poolSize ?? null}
        canExportView={selectedPeople === 0}
        exporting={exporting}
        onExportView={() => onExport([])}
      />

      <div className="flex min-h-0 flex-1">
        <PoolFilterRail
          open={filtersOpen}
          filters={filters}
          onChange={onFiltersChange}
          onClose={() => setFiltersOpen(false)}
          tags={lookups.offeredTags}
          staff={lookups.staff}
          positions={lookups.positions}
          countries={page.data?.countries ?? []}
          shown={page.data?.totalCount ?? null}
          pool={page.data?.poolSize ?? null}
        />

        <div className="flex min-w-0 flex-1 flex-col gap-3 p-2">
          {poolIsEmpty ? (
            <EmptyState
              icon={<Icon d={ICONS.candidates} size={22} />}
              title="No candidates yet"
              body="Everyone your team maps on a position lands here — added by hand, captured with the plugin, imported from a spreadsheet, or found by Find executives. Someone who is already here is added to the next position, never duplicated."
            >
              <Link to="/all" className="font-mono text-[12.5px] font-semibold text-u-accent hover:underline">
                Go to your positions
              </Link>
            </EmptyState>
          ) : (
            <>
              {!filtersOpen && (
                <ActiveFilters
                  filters={filters}
                  onChange={onFiltersChange}
                  tagsById={lookups.tagsById}
                  membersByUserId={lookups.membersByUserId}
                  positions={lookups.positions}
                />
              )}
              <div className="flex min-h-0 flex-1 flex-col">
                <DataGrid
                  table={table}
                  label="Candidates"
                  layout={layout}
                  onLayoutChange={setLayout}
                  loading={page.isPending}
                  error={page.isError}
                  errorMessage={page.isError ? messageFor(page.error) : "The candidates could not be loaded."}
                  emptyMessage="Nobody matches. Loosen a filter or clear the search."
                  onRowClick={(row) => onOpen(row.personId)}
                  headerLead={
                    <SelectionCheckbox
                      checked={rows.length > 0 && tickedOnPage === rows.length}
                      indeterminate={tickedOnPage > 0 && tickedOnPage < rows.length}
                      label="Select every person shown"
                      onChange={() =>
                        setRowSelection((current) => {
                          const next = { ...current };
                          const all = rows.length > 0 && rows.every((row) => current[row.personId]);
                          for (const row of rows) {
                            if (all) delete next[row.personId];
                            else next[row.personId] = true;
                          }
                          return next;
                        })
                      }
                    />
                  }
                  rowLead={(row) => (
                    <SelectionCheckbox
                      checked={Boolean(rowSelection[row.personId])}
                      label={`Select ${row.fullName}`}
                      onChange={table.getRow(row.personId).getToggleSelectedHandler()}
                    />
                  )}
                />
                {selectedPeople > 0 && (
                  <SelectionActionBar
                    count={selectedPeople}
                    noun="person"
                    plural="people"
                    onClear={() => setRowSelection(NOTHING_SELECTED)}
                  >
                    <SelectionAction icon={ICONS.position} label="Add to position" onClick={() => setDialog("position")} />
                    <SelectionAction icon={ICONS.tag} label="Tag" onClick={() => setDialog("tag")} />
                    <SelectionAction icon={ICONS.profile} label="Set owner" onClick={() => setDialog("owner")} />
                    <SelectionAction
                      icon={ICONS.exportOut}
                      label={exporting ? "Exporting…" : "Export"}
                      disabled={exporting}
                      onClick={() => onExport(selectedIds)}
                    />
                  </SelectionActionBar>
                )}
              </div>
              <PaginationBar
                page={paging.page}
                size={paging.size}
                totalCount={page.data?.totalCount ?? 0}
                onPage={paging.setPage}
                onSize={paging.setSize}
                trailing={<FullscreenButton active={isFullscreen} onToggle={toggleFullscreen} />}
              />
            </>
          )}
        </div>
      </div>

      {dialog === "position" && (
        <AddToPositionDialog
          open
          onClose={() => setDialog(null)}
          personIds={selectedIds}
          targetName={selectedPeople === 1 ? (rows.find((row) => rowSelection[row.personId])?.fullName ?? null) : null}
          alreadyInByPosition={alreadyInByPositionOf(rows, rowSelection)}
          positions={lookups.workablePositions}
          onDone={() => setRowSelection(NOTHING_SELECTED)}
        />
      )}
      {dialog === "tag" && (
        <TagPeopleDialog
          open
          onClose={() => setDialog(null)}
          personIds={selectedIds}
          holdersByTag={holdersByTagOf(rows, rowSelection)}
          tags={lookups.offeredTags}
          onDone={() => setRowSelection(NOTHING_SELECTED)}
        />
      )}
      {dialog === "owner" && (
        <SetOwnerDialog
          open
          onClose={() => setDialog(null)}
          personIds={selectedIds}
          staff={lookups.staff}
          onDone={() => setRowSelection(NOTHING_SELECTED)}
        />
      )}
    </div>
  );
}

/** How many of the ticked people on this page each position already holds. */
function alreadyInByPositionOf(rows: PoolRow[], selection: RowSelectionState): Map<string, number> {
  const counts = new Map<string, number>();
  for (const row of rows) {
    if (!selection[row.personId]) continue;
    for (const position of row.positions) {
      counts.set(position.projectId, (counts.get(position.projectId) ?? 0) + 1);
    }
  }
  return counts;
}

/** How many of the ticked people on this page hold each tag. */
function holdersByTagOf(rows: PoolRow[], selection: RowSelectionState): Map<string, number> {
  const counts = new Map<string, number>();
  for (const row of rows) {
    if (!selection[row.personId]) continue;
    for (const tagId of row.tagIds) counts.set(tagId, (counts.get(tagId) ?? 0) + 1);
  }
  return counts;
}
