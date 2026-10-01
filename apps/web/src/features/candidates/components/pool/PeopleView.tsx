import { keepPreviousData, useQuery } from "@tanstack/react-query";
import type { RowSelectionState } from "@tanstack/react-table";
import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { EmptyState } from "../../../../components/ui";
import { DataGrid } from "../../../../components/ui/DataGrid";
import { PaginationBar } from "../../../../components/ui/PaginationBar";
import { SelectionAction, SelectionActionBar } from "../../../../components/ui/SelectionActionBar";
import { SelectionCheckbox } from "../../../../components/ui/SelectionCheckbox";
import { cn } from "../../../../lib/cn";
import { messageFor } from "../../../../lib/errorCodes";
import { useDebouncedValue } from "../../../../lib/useComboboxList";
import { useDataGridTable } from "../../../../lib/useDataGridTable";
import { layoutColumnsOf, useGridLayout } from "../../../../lib/useGridLayout";
import { useGridPaging } from "../../../../lib/useGridPaging";
import * as poolApi from "../../api/poolApi";
import type { PoolFilters, PoolRow, PoolSortField, PoolView } from "../../api/types";
import { POOL_COLUMN_PINNING, poolColumnsFor, poolTableFeatures } from "../../lib/poolColumns";
import { countActiveFilters } from "../../lib/poolFilters";
import { usePoolLookups } from "../../lib/usePoolLookups";
import { AddToPositionDialog } from "./AddToPositionDialog";
import { ActiveFilters, PoolFilterPanel } from "./PoolFilterPanel";
import { SetOwnerDialog } from "./SetOwnerDialog";
import { TagPeopleDialog } from "./TagPeopleDialog";

const QUICK_VIEWS: { value: PoolView; label: string }[] = [
  { value: "all", label: "All" },
  { value: "mine", label: "Owned by me" },
  { value: "active", label: "In an active position" },
  { value: "unplaced", label: "Not in a position" },
];

const POOL_LAYOUT_COLUMNS = layoutColumnsOf(poolColumnsFor(new Map(), new Map()));
const NOTHING_SELECTED: RowSelectionState = {};

type BulkDialog = "position" | "tag" | "owner" | null;

/**
 * The People view of the Candidates page: everyone the workspace holds, searched, filtered and sorted
 * by the server, with the selection bar's bulk actions over the people ticked.
 */
export function PeopleView({
  filters,
  onFiltersChange,
  onOpen,
  onExport,
  exporting,
}: {
  filters: PoolFilters;
  onFiltersChange: (filters: PoolFilters) => void;
  onOpen: (personId: string) => void;
  onExport: (personIds: string[]) => void;
  exporting: boolean;
}) {
  const lookups = usePoolLookups();
  const paging = useGridPaging(50);
  const [query, setQuery] = useState(filters.q);
  const [filtersOpen, setFiltersOpen] = useState(false);
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

  if (page.isSuccess && page.data.poolSize === 0) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.candidates} size={22} />}
        title="No candidates yet"
        body="Everyone your team maps on a position lands here — added by hand, captured with the plugin, imported from a spreadsheet, or found by Find executives. Someone who is already here is added to the next position, never duplicated."
      >
        <Link to="/all" className="font-mono text-[12.5px] font-semibold text-u-accent hover:underline">
          Go to your positions
        </Link>
      </EmptyState>
    );
  }

  const activeFilterCount = countActiveFilters(filters);
  const tickedOnPage = rows.filter((row) => rowSelection[row.personId]).length;
  const selectedPeople = selectedIds.length;

  return (
    <div className="relative">
      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <div className="flex w-full items-center gap-2 rounded-lg border border-u-border-strong bg-u-raised px-[11px] py-[7px] sm:w-[320px]">
          <Icon d={ICONS.search} size={14} className="text-u-text3" />
          <input
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search name, title, company or email…"
            aria-label="Search candidates"
            className="w-full bg-transparent font-mono text-[13px] text-u-text outline-none placeholder:text-u-text3"
          />
        </div>
        <div className="flex flex-wrap gap-1.5" role="group" aria-label="Quick views">
          {QUICK_VIEWS.map((view) => (
            <button
              key={view.value}
              type="button"
              aria-pressed={filters.view === view.value}
              onClick={() => onFiltersChange({ ...filters, view: view.value })}
              className={cn(
                "rounded-full border px-[11px] py-[5px] font-mono text-xs font-medium transition hover:text-u-text",
                filters.view === view.value
                  ? "border-u-accent bg-u-accent-tint text-u-accent"
                  : "border-u-border-strong text-u-text2",
              )}
            >
              {view.label}
              {page.data && <span className="ms-1.5 text-u-text3">{page.data.viewCounts[view.value]}</span>}
            </button>
          ))}
        </div>
        <button
          type="button"
          aria-expanded={filtersOpen}
          onClick={() => setFiltersOpen((open) => !open)}
          className="ms-auto flex items-center gap-1.5 rounded-lg border border-u-border-strong px-3 py-[6px] font-mono text-xs font-medium text-u-text2 hover:text-u-text"
        >
          <Icon d={ICONS.filter} size={13} />
          Filters
          {activeFilterCount > 0 && (
            <span className="rounded-full bg-u-adjacent-tint px-1.5 font-mono text-[10.5px] text-u-adjacent">
              {activeFilterCount}
            </span>
          )}
        </button>
      </div>

      {filtersOpen && (
        <PoolFilterPanel
          filters={filters}
          onChange={onFiltersChange}
          tags={lookups.offeredTags}
          staff={lookups.staff}
          positions={lookups.positions}
          countries={page.data?.countries ?? []}
        />
      )}

      <ActiveFilters
        filters={filters}
        onChange={onFiltersChange}
        tagsById={lookups.tagsById}
        membersByUserId={lookups.membersByUserId}
        positions={lookups.positions}
        shown={page.data?.totalCount ?? null}
        pool={page.data?.poolSize ?? null}
      />

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

      <PaginationBar
        page={paging.page}
        size={paging.size}
        totalCount={page.data?.totalCount ?? 0}
        onPage={paging.setPage}
        onSize={paging.setSize}
        autoHide
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
