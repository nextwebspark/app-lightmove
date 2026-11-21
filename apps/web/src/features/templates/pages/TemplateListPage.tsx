import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FullscreenButton, useToast } from "../../../components/ui";
import { hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { GridFilterButton } from "../../../components/ui/GridFilters";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { cn } from "../../../lib/cn";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { FULLSCREEN_PANEL, useFullscreen } from "../../../lib/useFullscreen";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridPaging } from "../../../lib/useGridPaging";
import { useGridSort, WORKSPACE_SCOPE } from "../../../lib/useGridSort";
import { filterSpec, useLocalGridFilters, type LocalGridFilter } from "../../../lib/useLocalGridFilters";
import { SENIORITY_LABELS, SENIORITY_TIERS } from "../../../lib/seniority";
import { PlatformTwoFactorRequired } from "../../platform/components/PlatformTwoFactorRequired";
import { POSITION_TEMPLATES_KEY } from "../../position/api/positionApi";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateOverview, TemplateScope } from "../api/types";
import { ImportTemplatesDialog } from "../components/ImportTemplatesDialog";
import { TemplatesList } from "../components/TemplatesList";
import { TemplateToolbar } from "../components/TemplateToolbar";
import { filterTemplates, reviserOf } from "../lib/filtering";
import { DISCIPLINE_LABELS, DISCIPLINES, SCOPE_COPY } from "../lib/labels";
import {
  TEMPLATE_COLUMN_VISIBILITY,
  TEMPLATE_COLUMNS,
  TEMPLATE_SORT_FIELDS,
  type TemplateSortField,
} from "../lib/templateColumns";

const GRID_NAMESPACES: Record<TemplateScope, string> = {
  library: "template-library",
  workspace: "templates",
};

const LAYOUT_COLUMNS = {
  library: layoutColumnsOf(TEMPLATE_COLUMNS.library),
  workspace: layoutColumnsOf(TEMPLATE_COLUMNS.workspace),
};

const HIDEABLE_COLUMNS = {
  library: hideableColumnsOf(TEMPLATE_COLUMNS.library),
  workspace: hideableColumnsOf(TEMPLATE_COLUMNS.workspace),
};

const DEFAULT_TEMPLATE_SORT = { field: "discipline", direction: "asc" } as const;

/** Settings → Templates or Platform → Templates: every template in one searchable, sortable grid. */
export function TemplateListPage({ scope }: { scope: TemplateScope }) {
  // Both routes render this page in the same slot, so without the key a navigation between them
  // would keep the other scope's filter, sort and remembered columns.
  return <TemplateList key={scope} scope={scope} />;
}

function TemplateList({ scope }: { scope: TemplateScope }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();
  const [importing, setImporting] = useState(false);
  const [query, setQuery] = useState("");
  const namespace = GRID_NAMESPACES[scope];
  const [sort, setSort] = useGridSort<TemplateSortField>(
    namespace,
    WORKSPACE_SCOPE,
    TEMPLATE_SORT_FIELDS[scope],
    DEFAULT_TEMPLATE_SORT,
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    namespace,
    WORKSPACE_SCOPE,
    TEMPLATE_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout(namespace, LAYOUT_COLUMNS[scope]);
  const paging = useGridPaging();
  const [isFullscreen, toggleFullscreen] = useFullscreen();
  const { path } = SCOPE_COPY[scope];

  const { data: templates, isPending, isError, error } = useQuery({
    queryKey: templateApi.TEMPLATE_ADMIN_KEY(scope),
    queryFn: ({ signal }) => templateApi.listTemplates(scope, signal),
  });

  const needsTwoFactor = codeOf(error) === "PLATFORM_TWO_FACTOR_REQUIRED";

  const searched = useMemo(() => filterTemplates(templates ?? [], query), [templates, query]);
  const columnFilterSpecs = useMemo(() => templateColumnFilters(scope), [scope]);
  const { rows, filters: columnFilters, key: columnFilterKey } = useLocalGridFilters(searched, columnFilterSpecs);

  const { reset: resetPage, clampTo } = paging;
  useEffect(() => {
    resetPage();
  }, [resetPage, query, sort, columnFilterKey]);
  useEffect(() => {
    clampTo(rows.length);
  }, [clampTo, rows.length]);

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: templateApi.TEMPLATE_ADMIN_KEY(scope) });
    void queryClient.invalidateQueries({ queryKey: POSITION_TEMPLATES_KEY });
  };

  const visibility = useMutation({
    mutationFn: (template: TemplateOverview) =>
      scope === "library"
        ? templateApi.setTemplateActive(template.code, !template.active)
        : templateApi.setTemplateHidden(template.code, template.origin !== "HIDDEN"),
    onSuccess: (_, template) => {
      refresh();
      const message = visibilityMessage(scope, template);
      if (!isOffered(scope, template)) {
        toast(message);
        return;
      }
      // What Undo hands back is the template as it now stands, so the same toggle flips it back.
      const now = scope === "library" ? { ...template, active: false } : { ...template, origin: "HIDDEN" as const };
      toast.success(message, { undo: () => visibility.mutate(now) });
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  const exporting = useMutation({
    mutationFn: () => templateApi.exportTemplates(scope),
    onError: (error) => toast.error(messageFor(error)),
  });

  if (needsTwoFactor) return <PlatformTwoFactorRequired />;

  return (
    /* The shell hands grid pages the whole main area at a definite height (GRID_PAGES in
       SettingsLayout), so the rows scroll under the toolbar and pager rather than the page scrolling. */
    <div className={cn("flex min-h-0 flex-1 flex-col", isFullscreen && FULLSCREEN_PANEL)}>
      <TemplateToolbar
        scope={scope}
        query={query}
        onQuery={setQuery}
        filter={
          <GridFilterButton
            filters={columnFilters}
            resultLabel={`${rows.length} of ${searched.length} ${searched.length === 1 ? "template" : "templates"}`}
          />
        }
        hideableColumns={HIDEABLE_COLUMNS[scope]}
        columnVisibility={columnVisibility}
        onColumnVisibilityChange={setColumnVisibility}
        onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
        onImport={() => setImporting(true)}
        onExport={() => exporting.mutate()}
        exporting={exporting.isPending}
        onNew={() => navigate(`${path}/new`)}
      />

      {/* `min-h-0`, as on the Companies stages: without it the grid grows to every row it holds and the
          whole screen scrolls. Below `md` the rows are cards with no scroll box of their own. */}
      <div className="flex min-h-0 min-w-0 flex-1 flex-col gap-3 p-3 max-md:overflow-y-auto sm:p-5">
        <TemplatesList
          scope={scope}
          templates={rows}
          sort={sort}
          onSortChange={setSort}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          layout={layout}
          onLayoutChange={setLayout}
          pagination={paging.pagination}
          onPaginationChange={paging.onPaginationChange}
          loading={isPending}
          error={isError}
          isToggling={visibility.isPending}
          onToggle={(template) => visibility.mutate(template)}
          onOpen={(template) => navigate(`${path}/${encodeURIComponent(template.code)}`)}
          columnFilters={columnFilters}
        />
        <PaginationBar
          page={paging.page}
          size={paging.size}
          totalCount={templates ? rows.length : undefined}
          onPage={paging.setPage}
          onSize={paging.setSize}
          trailing={<FullscreenButton active={isFullscreen} onToggle={toggleFullscreen} />}
        />
      </div>

      <ImportTemplatesDialog
        open={importing}
        scope={scope}
        onClose={() => setImporting(false)}
        onImported={refresh}
      />
    </div>
  );
}

function templateColumnFilters(scope: TemplateScope): LocalGridFilter<TemplateOverview>[] {
  return [
    filterSpec<TemplateOverview, TemplateOverview["discipline"]>({
      id: "discipline",
      label: "Discipline",
      valuesOf: (template) => [template.discipline],
      labelOf: (discipline) => DISCIPLINE_LABELS[discipline],
      order: DISCIPLINES,
    }),
    filterSpec<TemplateOverview, TemplateOverview["seniority"]>({
      id: "seniority",
      label: "Seniority",
      valuesOf: (template) => [template.seniority],
      labelOf: (seniority) => SENIORITY_LABELS[seniority],
      order: SENIORITY_TIERS,
    }),
    filterSpec<TemplateOverview, string>({
      id: "revisedBy",
      label: "Revised by",
      valuesOf: (template) => {
        const reviser = reviserOf(scope, template);
        return reviser ? [reviser] : [];
      },
    }),
  ];
}

/** Whether a firm can pick it now: archiving or hiding takes it out of the picker, and that is what Undo undoes. */
function isOffered(scope: TemplateScope, template: TemplateOverview): boolean {
  return scope === "library" ? template.active : template.origin !== "HIDDEN";
}

function visibilityMessage(scope: TemplateScope, template: TemplateOverview): string {
  if (scope === "library") {
    return template.active ? "Archived — no workspace can pick it now" : "Restored to the library";
  }
  return template.origin === "HIDDEN" ? "Back in your firm's picker" : "Hidden from your firm's picker";
}
