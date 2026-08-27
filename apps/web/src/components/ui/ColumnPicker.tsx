import type { ColumnVisibilityState } from "@tanstack/react-table";
import { Icon, ICONS } from "../layout/Icon";
import { Popover } from "./Popover";
import { FilterCheckRow } from "./FilterCheckRow";

/** One column a user may turn off: the table's column id, and the header it is known by. */
export interface HideableColumn {
  id: string;
  label: string;
}

/**
 * A grid toolbar's Columns menu.
 *
 * <p>It takes the hideable columns rather than reading a table instance, because the menu lives in
 * the toolbar and the table lives beside the results — and because the two grids that use it hold
 * different columns. Each derives its own list from its column definitions with
 * {@link hideableColumnsOf}, so a column added there is offered here automatically.
 *
 * <p>The list scrolls: there are enough columns to run off the bottom of the screen.
 */
export function ColumnPicker({
  columns,
  visibility,
  defaults,
  onChange,
  onResetLayout,
}: {
  columns: HideableColumn[];
  visibility: ColumnVisibilityState;
  /** What "Reset to default" restores — the grid's own declared default, not an empty object. */
  defaults: ColumnVisibilityState;
  onChange: (visibility: ColumnVisibilityState) => void;
  /** Also puts back the widths and order the user dragged, so one button undoes every column change. */
  onResetLayout?: () => void;
}) {
  // Absent means visible: the state records only the exceptions.
  const isVisible = (id: string) => visibility[id] !== false;
  const hiddenCount = columns.filter((column) => !isVisible(column.id)).length;

  return (
    <Popover
      align="right"
      width={220}
      triggerClassName="inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] p-2 font-sans text-[13px] text-text3 transition hover:bg-panel hover:text-text"
      trigger={() => (
        <>
          <Icon d={ICONS.columns} size={14} className="flex-none" />
          Columns
          {hiddenCount > 0 && (
            <span className="rounded-[4px] bg-sky-dim px-[5px] py-[2px] font-sans text-[10px] font-bold text-sky">
              {hiddenCount} hidden
            </span>
          )}
        </>
      )}
    >
      {() => (
        <div className="flex max-h-[60vh] flex-col overflow-y-auto">
          {columns.map((column) => (
            <FilterCheckRow
              key={column.id}
              label={column.label}
              size="sm"
              checked={isVisible(column.id)}
              onToggle={() => onChange({ ...visibility, [column.id]: !isVisible(column.id) })}
            />
          ))}

          <div className="mx-1 my-1.5 h-px bg-line-soft" />

          <button
            type="button"
            onClick={() => {
              onChange(defaults);
              onResetLayout?.();
            }}
            className="rounded-[5px] px-1 py-[7px] text-left font-sans text-[12px] font-medium text-text3 transition hover:bg-panel2 hover:text-text"
          >
            Reset to default
          </button>
        </div>
      )}
    </Popover>
  );
}

/**
 * The columns a grid lets a user turn off, in the order the table lays them out. Columns that opt out
 * with `enableHiding: false` — the pinned name, the row actions — are never offered, because hiding
 * them would leave a row with nothing to identify or act on it.
 */
export function hideableColumnsOf(
  columns: readonly { id?: string; header?: unknown; enableHiding?: boolean }[],
): HideableColumn[] {
  return columns
    .filter((column) => column.enableHiding !== false)
    .map((column) => ({ id: column.id as string, label: String(column.header) }));
}
