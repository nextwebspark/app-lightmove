import type { Column } from "@tanstack/react-table";
import { useEffect, useRef, useState } from "react";
import { Icon } from "../../../components/layout/Icon";
import type { CompanyResult } from "../api/types";
import { COLUMN_GROUPS, type SourcingTableFeatures } from "./columns";

type SourcingColumn = Column<SourcingTableFeatures, CompanyResult, unknown>;

/**
 * Reads and writes the table's own visibility state rather than a parallel copy, so a column declaring
 * `enableHiding: false` drops out of the list on its own via `getCanHide()`.
 */
export function ColumnPicker({ columns, onReset }: { columns: SourcingColumn[]; onReset: () => void }) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const byId = new Map(columns.map((column) => [column.id, column]));
  const shownCount = columns.filter((column) => column.getIsVisible()).length;

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        onClick={() => setOpen((isOpen) => !isOpen)}
        aria-expanded={open}
        className="flex items-center gap-[6px] rounded-[8px] border border-line px-3 py-[6px] font-sans text-[12.5px] font-medium text-text2 hover:border-text3 hover:text-text"
      >
        <Icon d="M3 5h18M3 12h18M3 19h18M8 3v18" size={14} />
        Columns
      </button>

      {open && (
        <div className="absolute right-0 top-10 z-[80] max-h-[70vh] w-[250px] overflow-y-auto rounded-[10px] border border-line bg-panel p-1.5 shadow-panel">
          {COLUMN_GROUPS.map((group) => {
            const groupColumns = group.columnIds
              .map((columnId) => byId.get(columnId))
              .filter((column): column is SourcingColumn => column !== undefined && column.getCanHide());
            if (groupColumns.length === 0) {
              return null;
            }
            return (
              <div key={group.label} className="mb-1">
                <div className="px-2.5 pb-1 pt-2 font-mono text-[10px] font-semibold uppercase tracking-[0.1em] text-text3">
                  {group.label}
                </div>
                {groupColumns.map((column) => (
                  <label
                    key={column.id}
                    className="flex w-full cursor-pointer items-center gap-2.5 rounded-[7px] px-2.5 py-[7px] text-left text-[13px] text-text2 transition hover:bg-panel2 hover:text-text"
                  >
                    <input
                      type="checkbox"
                      checked={column.getIsVisible()}
                      onChange={column.getToggleVisibilityHandler()}
                      className="size-[13px] flex-none accent-amber"
                    />
                    {headerLabelOf(column)}
                  </label>
                ))}
              </div>
            );
          })}

          <div className="mx-1 my-1.5 h-px bg-line-soft" />
          <div className="flex items-center justify-between px-2.5 pb-1 pt-0.5">
            <span className="font-mono text-[11px] text-text3">{shownCount} shown</span>
            <button
              type="button"
              onClick={onReset}
              className="font-sans text-[12px] text-text2 hover:text-text"
            >
              Reset to default
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** The column's own header text, so the picker and the table cannot disagree on a name. */
function headerLabelOf(column: SourcingColumn): string {
  const header = column.columnDef.header;
  return typeof header === "string" ? header : (column.id ?? "");
}
