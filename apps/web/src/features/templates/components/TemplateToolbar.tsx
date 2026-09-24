import type { ColumnVisibilityState } from "@tanstack/react-table";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ColumnPicker, type HideableColumn } from "../../../components/ui/ColumnPicker";
import { Popover } from "../../../components/ui/Popover";
import type { TemplateScope } from "../api/types";
import { TEMPLATE_COLUMN_VISIBILITY } from "../lib/templateColumns";

const SCOPE_NOTES: Record<TemplateScope, string> = {
  library:
    "Changes here reach every workspace that hasn't customised the template. Workspaces with their own copy keep it and are told the library moved on. Mandates already drafted never change.",
  workspace:
    "Your firm starts from the LightMove library. Edit any template to make it your own — library updates stop reaching your copy until you reset it. Mandates already drafted never change.",
};

const TOOLBAR_BUTTON =
  "inline-flex items-center gap-1.5 whitespace-nowrap rounded-[6px] border border-u-border-strong bg-u-surface " +
  "px-3 py-2 font-sans text-[13px] font-medium text-u-text2 transition hover:border-u-text3 hover:text-u-text disabled:opacity-50";

/** The bar over a Templates grid, laid out as Strategy's and the Companies stages' toolbars are. */
export function TemplateToolbar({
  scope,
  query,
  onQuery,
  hideableColumns,
  columnVisibility,
  onColumnVisibilityChange,
  onResetLayout,
  onImport,
  onExport,
  exporting,
  onNew,
}: {
  scope: TemplateScope;
  query: string;
  onQuery: (query: string) => void;
  hideableColumns: HideableColumn[];
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: (visibility: ColumnVisibilityState) => void;
  onResetLayout: () => void;
  onImport: () => void;
  onExport: () => void;
  exporting: boolean;
  onNew: () => void;
}) {
  return (
    <div className="flex min-h-[44px] flex-none flex-wrap items-center gap-x-3.5 gap-y-2 border-b border-u-border bg-u-raised px-3 py-2 sm:px-5 sm:py-1.5">
      <div className="order-last flex w-full min-w-[180px] items-center gap-2 rounded-[6px] border border-u-border-strong px-3 py-2 sm:order-none sm:w-[240px] sm:flex-none">
        <Icon d={ICONS.search} size={14} className="flex-none text-u-text3" />
        <input
          value={query}
          onChange={(event) => onQuery(event.target.value)}
          placeholder="Search templates..."
          aria-label="Search templates"
          className="w-full bg-transparent font-sans text-[13px] text-u-text outline-none placeholder:text-u-text3"
        />
      </div>

      <div className="flex flex-wrap items-center gap-2 sm:ml-auto">
        <Popover
          align="right"
          width={320}
          label="About this list"
          triggerClassName="grid size-9 place-items-center rounded-[6px] text-u-text3 transition hover:bg-u-surface hover:text-u-text"
          trigger={() => <Icon d={ICONS.info} size={15} />}
        >
          {() => <p className="p-2 font-sans text-[12.5px] leading-relaxed text-u-text2">{SCOPE_NOTES[scope]}</p>}
        </Popover>

        {/* Below `md` the list is a card stack, and a Columns menu over cards has nothing to act on. */}
        <div className="hidden md:block">
          <ColumnPicker
            columns={hideableColumns}
            visibility={columnVisibility}
            defaults={TEMPLATE_COLUMN_VISIBILITY}
            onChange={onColumnVisibilityChange}
            onResetLayout={onResetLayout}
          />
        </div>

        <button type="button" onClick={onImport} className={TOOLBAR_BUTTON}>
          <Icon d={ICONS.importInto} size={14} className="flex-none" />
          Import
        </button>
        <button type="button" onClick={onExport} disabled={exporting} className={TOOLBAR_BUTTON}>
          <Icon d={ICONS.exportOut} size={14} className="flex-none" />
          Export
        </button>
        <button type="button" onClick={onNew} className={TOOLBAR_BUTTON}>
          <Icon d={ICONS.plus} size={14} className="flex-none" />
          New template
        </button>
      </div>
    </div>
  );
}
