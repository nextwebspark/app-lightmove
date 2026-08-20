import type { ColumnVisibilityState } from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Popover } from "../../../components/ui/Popover";
import { companyColumns, DEFAULT_COLUMN_VISIBILITY } from "../lib/companyColumns";
import { FilterCheckRow } from "./FilterCheckRow";

/** The columns a user is allowed to turn off, in the order the table lays them out. */
const HIDEABLE = companyColumns
  .filter((column) => column.enableHiding !== false)
  .map((column) => ({ id: column.id as string, label: String(column.header) }));

/**
 * The toolbar's Columns menu.
 *
 * <p><b>It reads the column definitions, not the table instance.</b> The table lives beside the
 * results and this button lives in the toolbar; passing a table instance up through the page to get
 * it here would couple the toolbar to the table's construction for a menu whose entire content is a
 * list of ids and labels the definitions already carry.
 *
 * <p>Company and Actions never appear: the first is the row's identity and the second is the only
 * thing the screen is for. Both say so with `enableHiding: false` on the column itself, so a new
 * column is offered here automatically unless it opts out.
 */
export function ColumnPicker({
  visibility,
  onChange,
}: {
  visibility: ColumnVisibilityState;
  onChange: (visibility: ColumnVisibilityState) => void;
}) {
  // Absent means visible: the state records exceptions, so a column added later shows up rather than
  // hiding behind a record written before it existed.
  const isVisible = (id: string) => visibility[id] !== false;
  const hiddenCount = HIDEABLE.filter((column) => !isVisible(column.id)).length;

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
              {hiddenCount}
            </span>
          )}
        </>
      )}
    >
      {() => (
        <div className="flex flex-col">
          {HIDEABLE.map((column) => (
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
            onClick={() => onChange(DEFAULT_COLUMN_VISIBILITY)}
            className="rounded-[5px] px-1 py-[7px] text-left font-sans text-[12px] font-medium text-text3 transition hover:bg-panel2 hover:text-text"
          >
            Reset to default
          </button>
        </div>
      )}
    </Popover>
  );
}
