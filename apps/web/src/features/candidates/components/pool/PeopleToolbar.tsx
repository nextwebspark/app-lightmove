import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { Chip } from "../../../../components/ui/Chip";
import { FilterRailToggle } from "../../../../components/ui/FilterRail";
import { ToolbarButton } from "../../../../components/ui/ToolbarButton";
import type { PoolView } from "../../api/types";

const QUICK_VIEWS: { value: PoolView; label: string }[] = [
  { value: "all", label: "All" },
  { value: "mine", label: "Owned by me" },
  { value: "active", label: "In an active position" },
  { value: "unplaced", label: "Not in a position" },
];

/**
 * The People view's strip, Strategy's toolbar: the view switch, the filter rail's toggle, the search,
 * the quick views and the count, then Export for everyone matching. While people are ticked the
 * selection bar's Export is the only one, so the two never stand side by side meaning different sets.
 */
export function PeopleToolbar({
  toggle,
  filtersOpen,
  onToggleFilters,
  activeFilterCount,
  query,
  onQueryChange,
  view,
  viewCounts,
  onViewChange,
  shown,
  pool,
  canExportView,
  exporting,
  onExportView,
}: {
  toggle: ReactNode;
  filtersOpen: boolean;
  onToggleFilters: () => void;
  activeFilterCount: number;
  query: string;
  onQueryChange: (query: string) => void;
  view: PoolView;
  viewCounts: Record<PoolView, number> | undefined;
  onViewChange: (view: PoolView) => void;
  shown: number | null;
  pool: number | null;
  canExportView: boolean;
  exporting: boolean;
  onExportView: () => void;
}) {
  return (
    <div className="flex min-h-[44px] flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border bg-u-raised px-3 py-2 sm:px-5 sm:py-1.5">
      {toggle}
      <FilterRailToggle
        open={filtersOpen}
        onToggle={onToggleFilters}
        badge={
          activeFilterCount > 0 && (
            <span className="rounded-[4px] bg-u-accent-tint px-[5px] py-[2px] font-sans text-[10px] font-bold text-u-accent">
              {activeFilterCount}
            </span>
          )
        }
      />
      <div className="flex w-full items-center gap-2 rounded-[6px] border border-u-border-strong bg-u-surface px-2.5 py-1.5 sm:w-[260px]">
        <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Search name, title, company or email…"
          aria-label="Search candidates"
          className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
        />
      </div>
      <div className="flex flex-wrap gap-1.5" role="group" aria-label="Quick views">
        {QUICK_VIEWS.map((option) => (
          <Chip
            key={option.value}
            size="sm"
            selected={view === option.value}
            aria-pressed={view === option.value}
            count={viewCounts?.[option.value]}
            onClick={() => onViewChange(option.value)}
          >
            {option.label}
          </Chip>
        ))}
      </div>
      <div className="flex items-center gap-3 sm:ms-auto">
        {shown !== null && pool !== null && (
          <span className="text-meta text-u-text3">
            {shown.toLocaleString()} of {pool.toLocaleString()} people
          </span>
        )}
        {canExportView && (
          <ToolbarButton
            loading={exporting}
            title="Download everyone matching this view as a CSV — recorded in the audit trail"
            onClick={onExportView}
          >
            <Icon d={ICONS.exportOut} size={14} />
            Export
          </ToolbarButton>
        )}
      </div>
    </div>
  );
}
