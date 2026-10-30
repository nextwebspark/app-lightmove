import type { ColumnVisibilityState } from "@tanstack/react-table";
import { Icon, ICONS } from "../layout/Icon";
import { cn } from "../../lib/cn";
import { Popover } from "./Popover";
import { FilterCheckRow } from "./FilterCheckRow";
import { TOOLBAR_COUNT, TOOLBAR_COUNT_ACCENT, TOOLBAR_MENU } from "./Toolbar";

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
  variant = "default",
}: {
  columns: HideableColumn[];
  visibility: ColumnVisibilityState;
  /** What "Reset to default" restores — the grid's own declared default, not an empty object. */
  defaults: ColumnVisibilityState;
  onChange: (visibility: ColumnVisibilityState) => void;
  /** Also puts back the widths and order the user dragged, so one button undoes every column change. */
  onResetLayout?: () => void;
  /** `toolbar` is the shared toolbar row's quiet menu (`Toolbar.tsx`), with a neutral count and a chevron. */
  variant?: "default" | "toolbar";
}) {
  const isToolbar = variant === "toolbar";
  // Absent means visible: the state records only the exceptions.
  const isVisible = (id: string) => visibility[id] !== false;
  const hiddenCount = columns.filter((column) => !isVisible(column.id)).length;

  return (
    <Popover
      align="right"
      width={220}
      triggerClassName={
        isToolbar
          ? TOOLBAR_MENU
          : "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] p-2 font-sans text-[13px] text-u-text3 transition hover:bg-u-surface hover:text-u-text"
      }
      trigger={(open) => (
        <>
          <Icon d={ICONS.columns} size={isToolbar ? 15 : 14} className="flex-none" />
          Columns
          {hiddenCount > 0 && (
            <span className={isToolbar ? TOOLBAR_COUNT : TOOLBAR_COUNT_ACCENT}>{hiddenCount} hidden</span>
          )}
          {isToolbar && (
            <Icon
              d={ICONS.chevronDown}
              size={12}
              className={cn("flex-none text-u-text3 transition-transform", open && "rotate-180")}
            />
          )}
        </>
      )}
    >
      {() => (
        <ColumnPickerList
          columns={columns}
          visibility={visibility}
          defaults={defaults}
          onChange={onChange}
          onResetLayout={onResetLayout}
        />
      )}
    </Popover>
  );
}

/** The picker's list on its own, for a menu that carries it as one of several panels. */
export function ColumnPickerList({
  columns,
  visibility,
  defaults,
  onChange,
  onResetLayout,
}: {
  columns: HideableColumn[];
  visibility: ColumnVisibilityState;
  defaults: ColumnVisibilityState;
  onChange: (visibility: ColumnVisibilityState) => void;
  onResetLayout?: () => void;
}) {
  const isVisible = (id: string) => visibility[id] !== false;
  return (
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

      <div className="mx-1 my-1.5 h-px bg-u-border" />

      <button
        type="button"
        onClick={() => {
          onChange(defaults);
          onResetLayout?.();
        }}
        className="rounded-[5px] px-1 py-[7px] text-left font-sans text-[12px] font-medium text-u-text3 transition hover:bg-u-raised hover:text-u-text"
      >
        Reset to default
      </button>
    </div>
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
