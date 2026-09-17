import type { ColumnVisibilityState, OnChangeFn, PaginationState } from "@tanstack/react-table";
import { DataGrid } from "../../../components/ui/DataGrid";
import { useDataGridTable } from "../../../lib/useDataGridTable";
import type { GridLayout } from "../../../lib/useGridLayout";
import type { GridSort } from "../../../lib/useGridSort";
import { SENIORITY_LABELS } from "../../../lib/seniority";
import type { TemplateOverview, TemplateScope } from "../api/types";
import { DISCIPLINE_LABELS } from "../lib/labels";
import {
  TEMPLATE_COLUMN_PINNING,
  TEMPLATE_COLUMNS,
  templateTableFeatures,
  type TemplateSortField,
} from "../lib/templateColumns";
import { TemplateActions } from "./TemplateActions";
import { LibraryUpdatedPill, TemplateBadge } from "./TemplateBadge";

/** A Templates list: the shared grid on a wide screen, a stack of cards below `md`. */
export function TemplatesList({
  scope,
  templates,
  sort,
  onSortChange,
  columnVisibility,
  onColumnVisibilityChange,
  layout,
  onLayoutChange,
  pagination,
  onPaginationChange,
  loading,
  error,
  isToggling,
  onToggle,
  onOpen,
}: {
  scope: TemplateScope;
  templates: TemplateOverview[];
  sort: GridSort<TemplateSortField>;
  onSortChange: (sort: GridSort<TemplateSortField>) => void;
  columnVisibility: ColumnVisibilityState;
  onColumnVisibilityChange: OnChangeFn<ColumnVisibilityState>;
  layout: GridLayout;
  onLayoutChange: (layout: GridLayout) => void;
  pagination: PaginationState;
  onPaginationChange: OnChangeFn<PaginationState>;
  loading: boolean;
  error: boolean;
  isToggling: boolean;
  onToggle: (template: TemplateOverview) => void;
  onOpen: (template: TemplateOverview) => void;
}) {
  const table = useDataGridTable<typeof templateTableFeatures, TemplateOverview, TemplateSortField>({
    features: templateTableFeatures,
    columns: TEMPLATE_COLUMNS[scope],
    data: templates,
    getRowId: (template) => template.code,
    pinning: TEMPLATE_COLUMN_PINNING,
    sort,
    onSortChange,
    columnVisibility,
    onColumnVisibilityChange,
    layout,
    onLayoutChange,
    pagination,
    onPaginationChange,
    meta: { isToggling, onToggle },
  });

  return (
    <DataGrid
      table={table}
      label="Templates"
      layout={layout}
      onLayoutChange={onLayoutChange}
      loading={loading}
      error={error}
      errorMessage="The templates could not be loaded. Refresh to try again."
      emptyMessage="No templates match. Clear the search or pick another filter."
      onRowClick={onOpen}
      renderCard={(template) => (
        <TemplateCard scope={scope} template={template} isToggling={isToggling} onToggle={onToggle} />
      )}
    />
  );
}

function TemplateCard({
  scope,
  template,
  isToggling,
  onToggle,
}: {
  scope: TemplateScope;
  template: TemplateOverview;
  isToggling: boolean;
  onToggle: (template: TemplateOverview) => void;
}) {
  return (
    <div className="rounded-[10px] border border-line bg-panel p-3.5">
      <div className="text-[13.5px] font-semibold text-text">{template.title}</div>
      <div className="mt-0.5 font-mono text-[11px] text-text3">
        {DISCIPLINE_LABELS[template.discipline]} · {SENIORITY_LABELS[template.seniority]}
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-1.5">
        <TemplateBadge scope={scope} template={template} />
        {template.libraryChangedSinceCustomised && <LibraryUpdatedPill />}
      </div>
      {template.summary && (
        <div className="mt-2 line-clamp-2 font-mono text-[11.5px] text-text3">{template.summary}</div>
      )}
      <div className="mt-2.5 border-t border-line-soft pt-2.5">
        <TemplateActions scope={scope} template={template} isToggling={isToggling} onToggle={onToggle} />
      </div>
    </div>
  );
}
