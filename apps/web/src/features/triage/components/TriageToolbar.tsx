import type { ColumnVisibilityState } from "@tanstack/react-table";
import { useMemo } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { SegmentedControl } from "../../../components/ui/SegmentedControl";
import type { CustomColumn } from "../../customcolumns/api/types";
import type { CompaniesView } from "../../talentmap/lib/useTalentMapPreferences";
import {
  createTriageCompanyColumns,
  defaultTriageColumnVisibility,
} from "../lib/triageCompanyColumns";

const TOOLBAR_BUTTON =
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] border border-line bg-panel " +
  "px-3 py-2 font-sans text-[13px] font-medium text-text2 transition hover:border-text3 hover:text-text";

/** The two readings of the screen, in the order they were built. */
const VIEW_OPTIONS = [
  { value: "table", label: "Table", icon: <Icon d={ICONS.table} size={13} /> },
  { value: "map", label: "Map", icon: <Icon d={ICONS.globe} size={13} /> },
] as const;

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
 *
 * <p>The Table | Map control appears only when the caller offers it — the universe page, on a
 * deployment with a Mapbox account. In Map view the column controls hide, because there are no
 * columns, and the search box narrows the panel and the pins instead of the grid.
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
  onImport,
  onManageColumns,
  canWrite,
  canImport,
  view = "table",
  onViewChange,
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
  onImport: () => void;
  onManageColumns: () => void;
  /** False for a client representative, who reads these grids and writes nothing. */
  canWrite: boolean;
  /** An imported company lands in the universe, so the other two stages do not offer the button. */
  canImport: boolean;
  /** How the screen is being read; the control renders only when `onViewChange` is given. */
  view?: CompaniesView;
  onViewChange?: (view: CompaniesView) => void;
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
          placeholder={view === "map" ? "Filter companies and executives..." : "Search companies..."}
          aria-label={view === "map" ? "Filter companies and executives" : "Search companies"}
          className="w-full bg-transparent font-sans text-[13px] text-text outline-none placeholder:text-text3"
        />
      </div>

      {onViewChange && (
        <SegmentedControl
          label="View"
          options={VIEW_OPTIONS}
          value={view}
          onChange={onViewChange}
        />
      )}

      <div className="flex items-center gap-3 sm:ml-auto">
        {view === "table" && (
          <ColumnPicker
            columns={hideableColumns}
            visibility={columnVisibility}
            defaults={defaults}
            onChange={onColumnVisibilityChange}
            onResetLayout={onResetLayout}
          />
        )}

        {canWrite && (
          <>
            {view === "table" && (
              <button type="button" onClick={onManageColumns} className={TOOLBAR_BUTTON}>
                <Icon d={ICONS.settings} size={14} className="flex-none" />
                Columns
              </button>
            )}
            {canImport && (
              <button type="button" onClick={onImport} className={TOOLBAR_BUTTON}>
                <Icon d={ICONS.uploadCloud} size={14} className="flex-none" />
                Import
              </button>
            )}
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
