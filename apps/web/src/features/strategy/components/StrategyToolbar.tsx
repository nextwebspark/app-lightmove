import type { ColumnVisibilityState } from "@tanstack/react-table";
import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { SavedSearch, SearchVisibility, StrategyFilter } from "../api/types";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { FilterRailToggle } from "../../../components/ui/FilterRail";
import { TOOLBAR_COUNT, TOOLBAR_OUTLINE, TOOLBAR_ROW, ToolbarLabel, ToolbarSearch } from "../../../components/ui/Toolbar";
import { cn } from "../../../lib/cn";
import { SaveSearchMenu } from "./SaveSearchMenu";
import { sameFilter } from "../lib/filterIdentity";
import { appliedRange, savableFilter } from "../lib/filterRanges";
import { companyColumns, DEFAULT_COLUMN_VISIBILITY } from "../lib/companyColumns";

/** Derived once: the column definitions are a module constant, not per-render state. */
const HIDEABLE_COMPANY_COLUMNS = hideableColumnsOf(companyColumns);

/**
 * How many accordions carry a selection — the count beside Show Filters.
 *
 * <p>An axis is one accordion, so its bands and its custom range count once between them: they are
 * two modes of the same panel and choosing a range clears the bands.
 */
function activeAxisCount(filter: StrategyFilter): number {
  return [
    filter.industries.length > 0 || filter.keywords.length > 0,
    filter.marketSegments.length > 0,
    filter.countries.length > 0,
    filter.employeeBands.length > 0 || appliedRange(filter.employeeRange) !== null,
    filter.revenueBands.length > 0 || appliedRange(filter.revenueRange) !== null,
  ].filter(Boolean).length;
}

export function StrategyToolbar({
  leading,
  filter,
  filterPending,
  searches,
  viewerId,
  showFilters,
  onToggleFilters,
  query,
  onQuery,
  onQueryBlur,
  onSaveSearch,
  onLoadSearch,
  onRenameSearch,
  onSetSearchVisibility,
  onOverwriteSearch,
  onDeleteSearch,
  onAiResearch,
  aiResearchOpen,
  columnVisibility,
  onColumnVisibilityChange,
  onResetLayout,
  savingSearch,
}: {
  /** The Companies | People toggle, when the viewer may search people. */
  leading?: ReactNode;
  filter: StrategyFilter;
  /** The stored filter has not landed yet, so the count would state a selection nobody made. */
  filterPending: boolean;
  searches: SavedSearch[];
  viewerId: string | null;
  showFilters: boolean;
  onToggleFilters: () => void;
  query: string;
  onQuery: (query: string) => void;
  /** Leaving the box writes the search to the address at once, so a row opened straight after carries it into Back. */
  onQueryBlur: () => void;
  onSaveSearch: (name: string, visibility: SearchVisibility) => void;
  onLoadSearch: (filter: StrategyFilter) => void;
  onRenameSearch: (searchId: string, name: string) => void;
  onSetSearchVisibility: (searchId: string, visibility: SearchVisibility) => void;
  onOverwriteSearch: (searchId: string) => void;
  onDeleteSearch: (searchId: string) => void;
  onAiResearch: () => void;
  /** The assistant is docked beside the page, so the button reads pressed. */
  aiResearchOpen: boolean;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: (visibility: ColumnVisibilityState) => void;
  onResetLayout: () => void;
  savingSearch: boolean;
}) {
  return (
    <div className={TOOLBAR_ROW}>
      {leading}
      <div className="contents" onBlur={onQueryBlur}>
        <ToolbarSearch value={query} onChange={onQuery} label="Search companies" placeholder="Search companies..." />
      </div>

      <SaveSearchMenu
        searches={searches}
        // Against what the server holds: an inverted range on screen is saved as none.
        isActive={(search) => sameFilter(savableFilter(filter), search.filter)}
        viewerId={viewerId}
        onSave={onSaveSearch}
        onLoad={(search) => onLoadSearch(search.filter)}
        onRename={onRenameSearch}
        onSetVisibility={onSetSearchVisibility}
        onOverwrite={onOverwriteSearch}
        onDelete={onDeleteSearch}
        saving={savingSearch}
      />

      <FilterRailToggle
        open={showFilters}
        onToggle={onToggleFilters}
        variant="toolbar"
        badge={
          filterPending ? (
            <span className="h-[15px] w-[15px] animate-pulse rounded-u-control bg-u-raised" />
          ) : (
            <span className={TOOLBAR_COUNT}>{activeAxisCount(filter)}</span>
          )
        }
      />

      <div className="flex flex-none items-center gap-3 sm:ml-auto">
        {/* Hidden below `lg` with the panel it opens: the assistant has no layout on a phone or a tablet yet, so
            here it would set the open flag and show nothing at all. One of the toolbar's own bordered controls, the
            AI told by the sparkle's violet alone; pressed while the panel is open, and a second press closes it. */}
        <button
          type="button"
          onClick={onAiResearch}
          aria-pressed={aiResearchOpen}
          title="AI Research"
          className={cn(
            TOOLBAR_OUTLINE,
            "hidden lg:inline-flex",
            "aria-pressed:border-u-inferred/50 aria-pressed:bg-u-inferred-tint aria-pressed:text-u-inferred",
          )}
        >
          <Icon d={ICONS.sparkle} size={15} className="flex-none text-u-inferred" />
          <ToolbarLabel>AI Research</ToolbarLabel>
        </button>

        <ColumnPicker
          columns={HIDEABLE_COMPANY_COLUMNS}
          visibility={columnVisibility}
          defaults={DEFAULT_COLUMN_VISIBILITY}
          onChange={onColumnVisibilityChange}
          onResetLayout={onResetLayout}
          variant="toolbar"
        />
      </div>
    </div>
  );
}
