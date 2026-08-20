import type { ColumnVisibilityState } from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import type { SavedSearch, StrategyFilter } from "../api/types";
import { ColumnPicker } from "./ColumnPicker";
import { SaveSearchMenu } from "./SaveSearchMenu";

/** How many accordions carry a selection — the mockup's sky badge beside Show Filters. */
function activeAxisCount(filter: StrategyFilter): number {
  return [
    filter.industries,
    filter.marketSegments,
    filter.countries,
    filter.employeeBands,
    filter.revenueBands,
  ].filter((axis) => axis.length > 0).length;
}

export function StrategyToolbar({
  filter,
  searches,
  showFilters,
  onToggleFilters,
  query,
  onQuery,
  onSaveSearch,
  onLoadSearch,
  onDeleteSearch,
  onAddAll,
  onAiResearch,
  columnVisibility,
  onColumnVisibilityChange,
  savingSearch,
  addingAll,
}: {
  filter: StrategyFilter;
  searches: SavedSearch[];
  showFilters: boolean;
  onToggleFilters: () => void;
  query: string;
  onQuery: (query: string) => void;
  onSaveSearch: (name: string) => void;
  onLoadSearch: (filter: StrategyFilter) => void;
  onDeleteSearch: (searchId: string) => void;
  onAddAll: () => void;
  onAiResearch: () => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: (visibility: ColumnVisibilityState) => void;
  savingSearch: boolean;
  addingAll: boolean;
}) {
  return (
    <div className="flex min-h-[44px] flex-none items-center gap-3.5 border-b border-line-soft bg-panel2 px-5 py-1.5">
      <SaveSearchMenu
        searches={searches}
        onSave={onSaveSearch}
        onLoad={onLoadSearch}
        onDelete={onDeleteSearch}
        saving={savingSearch}
      />

      <button
        type="button"
        onClick={onToggleFilters}
        aria-expanded={showFilters}
        className="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] p-2 font-sans text-[13px] text-text3 transition hover:bg-panel hover:text-text"
      >
        <Icon d="M3 4h18l-7 8v6l-4 2v-8L3 4Z" size={14} className="flex-none" />
        {showFilters ? "Hide Filters" : "Show Filters"}
        <span className="rounded-[4px] bg-sky-dim px-[5px] py-[2px] font-sans text-[10px] font-bold text-sky">
          {activeAxisCount(filter)}
        </span>
      </button>

      {/* The only filled CTA in the toolbar, and the mockup's gradient is the whole point of it —
          it is the affordance the screen is selling. No endpoint behind it yet. */}
      <button
        type="button"
        onClick={onAiResearch}
        className="inline-flex items-center gap-2 whitespace-nowrap rounded-[6px] border border-[#4f46e5] bg-[linear-gradient(90deg,#6366f1,#3b82f6)] px-4 py-2 font-sans text-[13px] font-semibold text-white shadow-[0_4px_12px_-2px_rgba(0,0,0,.15)] transition hover:brightness-105"
      >
        <Icon
          d="M9.9 2.6 11 5.9a2 2 0 0 0 1.3 1.3l3.3 1.1-3.3 1.1a2 2 0 0 0-1.3 1.3L9.9 14l-1.1-3.3a2 2 0 0 0-1.3-1.3L4.2 8.3l3.3-1.1a2 2 0 0 0 1.3-1.3ZM18 14l.6 1.8 1.8.6-1.8.6-.6 1.8-.6-1.8-1.8-.6 1.8-.6Z"
          size={15}
          className="flex-none"
        />
        AI Research
      </button>

      <div className="flex w-[240px] flex-none items-center gap-2 rounded-[6px] border border-line px-3 py-2">
        <Icon d={ICONS.search} size={14} className="flex-none text-text3" />
        <input
          value={query}
          onChange={(event) => onQuery(event.target.value)}
          placeholder="Search companies..."
          aria-label="Search companies"
          className="w-full bg-transparent font-sans text-[13px] text-text outline-none placeholder:text-text3"
        />
      </div>

      <div className="ml-auto flex items-center gap-3">
        <ColumnPicker visibility={columnVisibility} onChange={onColumnVisibilityChange} />

        <button
          type="button"
          onClick={onAddAll}
          disabled={addingAll}
          className="whitespace-nowrap font-sans text-[13px] text-text3 transition hover:text-text disabled:opacity-40"
        >
          Add all to Universe →
        </button>
      </div>
    </div>
  );
}
