import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../../components/layout/Icon";
import { ToolbarButton } from "../../../../components/ui/ToolbarButton";

/**
 * The People view's strip: the view switch, the search, the Filter button and Columns menu every grid
 * carries, the count, then Export for everyone matching. While people are ticked the
 * selection bar's Export is the only one, so the two never stand side by side meaning different sets.
 */
export function PeopleToolbar({
  toggle,
  filter,
  columns,
  query,
  onQueryChange,
  shown,
  pool,
  canExportView,
  exporting,
  onExportView,
}: {
  toggle: ReactNode;
  /** The grid's `GridFilterButton`. */
  filter: ReactNode;
  /** The grid's `ColumnPicker`. */
  columns: ReactNode;
  query: string;
  onQueryChange: (query: string) => void;
  shown: number | null;
  pool: number | null;
  canExportView: boolean;
  exporting: boolean;
  onExportView: () => void;
}) {
  return (
    <div className="flex min-h-[44px] flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border bg-u-raised px-3 py-2 sm:px-5 sm:py-1.5">
      {toggle}
      <label className="flex w-full items-center gap-2 rounded-[6px] border border-u-border-strong bg-u-surface px-2.5 py-1.5 sm:w-[260px]">
        <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
        <input
          value={query}
          onChange={(event) => onQueryChange(event.target.value)}
          placeholder="Search name, title, company or email…"
          aria-label="Search candidates"
          className="w-full bg-transparent text-note text-u-text outline-none placeholder:text-u-text3"
        />
      </label>
      {filter}
      <div className="flex items-center gap-3 sm:ms-auto">
        {columns}
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
