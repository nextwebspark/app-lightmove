import type { ColumnVisibilityState } from "@tanstack/react-table";
import { useMemo } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import type { CustomColumn } from "../../customcolumns/api/types";
import {
  createTriageCompanyColumns,
  defaultTriageColumnVisibility,
} from "../lib/triageCompanyColumns";

const TOOLBAR_BUTTON =
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] border border-line bg-panel " +
  "px-3 py-2 font-sans text-[13px] font-medium text-text2 transition hover:border-text3 hover:text-text";



/**
 * The Companies grid's toolbar — the same bar Strategy carries, holding the two controls that mean
 * the same thing on both screens (search, Columns) and the one that only makes sense here.
 *
 * <p>The two "Add" buttons are those. Strategy's equivalent takes a company out of the market; there
 * is no market behind either of these, which is the point — they are how a company the export does not
 * carry, and an executive no export has ever carried, get into a mandate at all.
 *
 * <p>"Add executive" here maps someone with no company selected, so the row lands unmapped: the
 * executive a researcher met at a company this mandate never triaged. Adding someone <i>at</i> a
 * company is that company's own row action, where the company is already known.
 */
export function TriageToolbar({
  query,
  onQuery,
  columnVisibility,
  onColumnVisibilityChange,
  customColumns,
  onResetLayout,
  onAddCompany,
  onAddExecutive,
  onManageColumns,
  canWrite,
}: {
  query: string;
  onQuery: (query: string) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: (visibility: ColumnVisibilityState) => void;
  /** This mandate's own extra columns — they are pickable and resettable like any built-in. */
  customColumns: readonly CustomColumn[];
  onResetLayout: () => void;
  onAddCompany: () => void;
  onAddExecutive: () => void;
  onManageColumns: () => void;
  /** False for a client representative, who reads these grids and writes nothing. */
  canWrite: boolean;
}) {
  // Derived from the project's own column set, so a custom column appears in the picker with the
  // built-ins rather than being the one column on the grid nobody can hide.
  const hideableColumns = useMemo(
    () => hideableColumnsOf(createTriageCompanyColumns(customColumns)),
    [customColumns],
  );
  const defaults = useMemo(() => defaultTriageColumnVisibility(customColumns), [customColumns]);

  return (
    <div className="flex min-h-[44px] flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-line-soft bg-panel2 px-3 py-2 sm:px-5 sm:py-1.5">
      <div className="order-last flex w-full min-w-[180px] items-center gap-2 rounded-[6px] border border-line px-3 py-2 sm:order-none sm:w-[240px] sm:flex-none">
        <Icon d={ICONS.search} size={14} className="flex-none text-text3" />
        <input
          value={query}
          onChange={(event) => onQuery(event.target.value)}
          placeholder="Search companies..."
          aria-label="Search companies"
          className="w-full bg-transparent font-sans text-[13px] text-text outline-none placeholder:text-text3"
        />
      </div>

      <div className="flex items-center gap-3 sm:ml-auto">
        <ColumnPicker
          columns={hideableColumns}
          visibility={columnVisibility}
          defaults={defaults}
          onChange={onColumnVisibilityChange}
          onResetLayout={onResetLayout}
        />

        {canWrite && (
          <>
            <button type="button" onClick={onManageColumns} className={TOOLBAR_BUTTON}>
              <Icon d={ICONS.settings} size={14} className="flex-none" />
              Columns
            </button>
            <button
              type="button"
              onClick={onAddExecutive}
              className={TOOLBAR_BUTTON}
            >
              <Icon d={ICONS.userPlus} size={14} className="flex-none" />
              Add executive
            </button>
            <button type="button" onClick={onAddCompany} className={TOOLBAR_BUTTON}>
              <Icon d={ICONS.plus} size={14} className="flex-none" />
              Add company
            </button>
          </>
        )}
      </div>
    </div>
  );
}
