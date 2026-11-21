import { useQuery } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import * as dashboardsApi from "../api/dashboardsApi";
import type { ChartQueryDef, Dashboard, DashboardScope, DashboardWidget, WidgetLayout } from "../api/types";
import { clampSize, compact, limitsFor, place, settledWidgets, startingLayout } from "../lib/gridLayout";
import { bodyOf, useDashboardWrites } from "../lib/useDashboardWrites";

/** Figures stay live without a refetch on every focus: each answer is a server-side aggregate. */
const ANSWERS_STALE_MS = 60_000;
import { ChartCard } from "./ChartCard";
import { ChartLibraryDrawer, draftFor, type ChartDraft } from "./ChartLibraryDrawer";
import { DashboardGrid } from "./DashboardGrid";
import { DashboardNameDialog } from "./DashboardNameDialog";

export const MAX_CHARTS = 30;

interface DraftItem {
  key: string;
  widget: DashboardWidget & { layout: WidgetLayout };
}

let keySequence = 0;
const keyed = (widget: DraftItem["widget"]): DraftItem => ({ key: `chart-${++keySequence}`, widget });

const placed = (widgets: DashboardWidget[]): DraftItem["widget"][] => settledWidgets(widgets);

function relaid(items: DraftItem[], layouts: WidgetLayout[]): DraftItem[] {
  return items.map((item, index) => ({ ...item, widget: { ...item.widget, layout: layouts[index] } }));
}

/**
 * One custom dashboard: its charts on a twelve-column grid, and — for its owner — Edit layout (drag, resize, remove),
 * the chart library, and rename / duplicate / share / delete. A teammate's shared dashboard is read-only.
 */
export function DashboardView({
  scope,
  dashboard,
  library,
  liveNote,
  sharedWith,
  onOpen,
  onDeleted,
  tabs,
}: {
  /** The page's dashboard tabs; drawn in place of the title, beside the actions. */
  tabs?: ReactNode;
  scope: DashboardScope;
  dashboard: Dashboard;
  library: ChartQueryDef[];
  /** "live from the mandate's rows" / "live across every open position". */
  liveNote: string;
  /** Who a shared dashboard reaches: "the position's team", "the workspace team". */
  sharedWith: string;
  onOpen: (id: string) => void;
  onDeleted: () => void;
}) {
  const toast = useToast();
  const { duplicate, save, remove } = useDashboardWrites(scope);
  const [draft, setDraft] = useState<DraftItem[] | null>(null);
  const [libraryDraft, setLibraryDraft] = useState<ChartDraft | null | "library">(null);
  const [dialog, setDialog] = useState<"rename" | "delete" | null>(null);
  const editing = draft !== null;
  const items = draft ?? placed(dashboard.widgets).map((widget, index) => ({ key: `saved-${index}`, widget }));
  const queryOf = (id: string) => library.find((query) => query.id === id);
  const asked = items.map((item) => item.widget);
  const answers = useQuery({
    queryKey: dashboardsApi.CHART_ANSWERS_KEY(scope, asked),
    queryFn: ({ signal }) => dashboardsApi.getChartAnswers(scope, asked, signal),
    enabled: asked.length > 0,
    staleTime: ANSWERS_STALE_MS,
  });

  const startEditing = (extra: DraftItem["widget"][] = []) =>
    setDraft([...(draft ?? placed(dashboard.widgets).map(keyed)), ...extra.map(keyed)]);

  const handleSave = () => {
    if (!draft) return;
    save.mutate(
      { id: dashboard.id, body: bodyOf(dashboard, { widgets: draft.map((item) => item.widget) }) },
      {
        onSuccess: () => {
          setDraft(null);
          toast.success("Dashboard saved");
        },
      },
    );
  };

  const handleCommit = (chart: ChartDraft) => {
    const layouts = items.map((item) => item.widget.layout);
    const widget = {
      queryId: chart.query.id,
      chart: chart.chart,
      options: chart.options,
      title: chart.title.trim() || null,
      layout: chart.index === null
        ? startingLayout(layouts, chart.chart)
        : clampSize(items[chart.index].widget.layout, limitsFor(chart.chart)),
    };
    if (chart.index === null) {
      if (items.length >= MAX_CHARTS) {
        toast.error(`A dashboard can hold up to ${MAX_CHARTS} charts. Remove one to add another.`);
        return;
      }
      startEditing([widget]);
      return;
    }
    const settled = compact(place(layouts, chart.index, widget.layout));
    const updated = relaid(items.map((item, index) => (index === chart.index ? { ...item, widget } : item)), settled);
    if (editing) {
      setDraft(updated);
      return;
    }
    const widgets = updated.map((item) => item.widget);
    save.mutate({ id: dashboard.id, body: bodyOf(dashboard, { widgets }) }, { onSuccess: () => toast.success("Chart updated") });
  };

  const removeChart = (index: number) => {
    const kept = items.filter((_, other) => other !== index);
    setDraft(relaid(kept, compact(kept.map((item) => item.widget.layout))));
  };

  const editChart = (index: number) => {
    const query = queryOf(items[index].widget.queryId);
    if (query) setLibraryDraft(draftFor(query, items[index].widget, index));
  };

  const handleDuplicate = () =>
    duplicate.mutate(dashboard.id, {
      onSuccess: (copy) => {
        toast.success("Dashboard duplicated");
        onOpen(copy.id);
      },
    });

  const status = (
    <p className={cn("flex items-center gap-1.5 text-note text-u-text3", tabs ? "mt-4" : "mt-1.5")}>
      <Icon d={dashboard.shared ? ICONS.team : ICONS.lock} size={13} />
      {!dashboard.owned
        ? `Shared by a teammate — read only · ${liveNote}`
        : dashboard.shared
          ? `Shared with ${sharedWith} · ${liveNote}`
          : `Only you can see this dashboard · ${liveNote}`}
    </p>
  );

  const actions = (
    <>
      {dashboard.owned && !editing && (
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" className="hidden md:inline-flex" onClick={() => startEditing()}>
            <Icon d={ICONS.dashboard} size={14} />
            Edit layout
          </Button>
          <ActionMenu label="Dashboard options" triggerClassName="size-[34px] border-u-border-strong">
            {(close) => (
              <>
                <MenuItem icon={ICONS.team} onSelect={() => {
                  close();
                  save.mutate({ id: dashboard.id, body: bodyOf(dashboard, { shared: !dashboard.shared }) }, {
                    onSuccess: () => toast.success(dashboard.shared ? "Only you can see this dashboard now" : `Shared with ${sharedWith}`),
                  });
                }}>
                  {dashboard.shared ? "Stop sharing" : "Share with team"}
                </MenuItem>
                <MenuItem icon={ICONS.pencil} onSelect={() => { close(); setDialog("rename"); }}>Rename</MenuItem>
                <MenuItem icon={ICONS.copy} onSelect={() => { close(); handleDuplicate(); }}>Duplicate</MenuItem>
                <MenuSeparator />
                <MenuItem icon={ICONS.trash} tone="danger" onSelect={() => { close(); setDialog("delete"); }}>
                  Delete dashboard
                </MenuItem>
              </>
            )}
          </ActionMenu>
        </div>
      )}
      {!dashboard.owned && (
        <Button variant="secondary" size="sm" loading={duplicate.isPending} onClick={handleDuplicate}>
          <Icon d={ICONS.copy} size={14} />
          Duplicate to edit
        </Button>
      )}
      {editing && (
        <div className="flex items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => setLibraryDraft("library")}>
            <Icon d={ICONS.plus} size={14} />
            Add chart
          </Button>
          <Button variant="secondary" size="sm" onClick={() => setDraft(null)}>
            Cancel
          </Button>
          <Button size="sm" loading={save.isPending} onClick={handleSave}>
            Save layout
          </Button>
        </div>
      )}
    </>
  );

  return (
    <section aria-label={dashboard.name} className={cn(tabs ? "flex min-h-0 flex-1 flex-col" : "animate-fade-up")}>
      {tabs ? (
        <div className="flex flex-none flex-wrap-reverse items-end justify-between gap-x-3 gap-y-2 border-b border-u-border bg-u-surface px-4 pt-2.5">
          <div className="-mb-px min-w-0 flex-1">{tabs}</div>
          <div className="flex flex-none items-center gap-2 self-center">{actions}</div>
        </div>
      ) : (
        <div className="flex flex-wrap items-start justify-between gap-x-5 gap-y-3.5">
          <div className="min-w-0">
            <h1 className="type-title">{dashboard.name}</h1>
            {status}
          </div>
          {actions}
        </div>
      )}

      <div className={cn(tabs && "min-h-0 flex-1 overflow-y-auto px-4 pb-[60px] sm:px-7")}>
        {tabs && status}
        {editing && (
          <p className="mt-4 rounded-u-chip bg-u-accent-tint px-3.5 py-2.5 text-note text-u-text2">
            Drag a chart anywhere to move it, and its edge or corner to resize it. The pencil changes its chart type and
            options. Nothing changes for anyone else until you save.
          </p>
        )}

        {items.length === 0 ? (
          <div className="mt-5 flex flex-col items-center rounded-u-card border-[1.5px] border-dashed border-u-border-strong px-5 py-16 text-center">
            <div className="mb-4 grid size-12 place-items-center rounded-u-card bg-u-accent-tint text-u-accent">
              <Icon d={ICONS.dashboard} size={22} />
            </div>
            <div className="text-lead font-semibold">Nothing on this dashboard yet</div>
            <p className="mb-4 mt-2 max-w-[420px] text-body text-u-text2">
              Pick from the library of ready-made charts, choose how each one is drawn, then arrange them your way.
            </p>
            {dashboard.owned && (
              <Button onClick={() => setLibraryDraft("library")}>
                <Icon d={ICONS.plus} size={14} />
                Add chart
              </Button>
            )}
          </div>
        ) : (
          <>
            <DashboardGrid
              editing={editing}
              items={items.map((item) => ({
                key: item.key,
                layout: item.widget.layout,
                limits: limitsFor(item.widget.chart),
                label: item.widget.title || queryOf(item.widget.queryId)?.label || "Chart",
              }))}
              onChange={(layouts) => setDraft(relaid(items, layouts))}
            >
              {(index) => {
                const item = items[index];
                return (
                  <ChartCard
                    scope={scope}
                    widget={item.widget}
                    query={queryOf(item.widget.queryId)}
                    answer={answers.data?.[index]}
                    isPending={answers.isPending}
                    isError={answers.isError}
                    className={cn(editing && "border-u-accent-ring shadow-u-e1 group-hover/chart:border-u-accent")}
                    actions={
                      editing ? (
                        <>
                          {queryOf(item.widget.queryId) && (
                            <IconButton label="Edit chart" icon={ICONS.pencil} onClick={() => editChart(index)} />
                          )}
                          <IconButton label="Remove chart" icon={ICONS.close} tone="danger" onClick={() => removeChart(index)} />
                        </>
                      ) : dashboard.owned && queryOf(item.widget.queryId) ? (
                        <IconButton label="Edit chart" icon={ICONS.pencil} onClick={() => editChart(index)} />
                      ) : undefined
                    }
                  />
                );
              }}
            </DashboardGrid>
            {editing && (
              <button type="button" onClick={() => setLibraryDraft("library")}
                      className="mt-3.5 flex w-full items-center justify-center gap-2 rounded-u-card border-[1.5px] border-dashed border-u-border-strong p-5 text-body font-medium text-u-text2 hover:border-u-accent-ring hover:text-u-accent">
                <Icon d={ICONS.plus} size={15} />
                Add chart
              </button>
            )}
          </>
        )}
      </div>

      {/* Portalled: this section's fade-up keeps a transform, which would pin a fixed drawer or dialog inside it. */}
      {createPortal(
        <>
          {libraryDraft !== null && (
            <ChartLibraryDrawer
              scope={scope}
              library={library}
              onCountLabel={`${library.length} ready-made charts · ${items.length} on this dashboard`}
              initialDraft={libraryDraft === "library" ? null : libraryDraft}
              onCommit={handleCommit}
              onClose={() => setLibraryDraft(null)}
            />
          )}
          {dialog === "rename" && (
            <DashboardNameDialog
              title="Rename dashboard"
              submitLabel="Save"
              initialName={dashboard.name}
              pending={save.isPending}
              onClose={() => setDialog(null)}
              onSubmit={(name) =>
                save.mutate({ id: dashboard.id, body: bodyOf(dashboard, { name }) }, {
                  onSuccess: () => {
                    setDialog(null);
                    toast.success("Dashboard renamed");
                  },
                })
              }
            />
          )}
          <ConfirmDialog
            open={dialog === "delete"}
            title={`Delete “${dashboard.name}”?`}
            confirmLabel="Delete dashboard"
            tone="danger"
            pending={remove.isPending}
            onClose={() => setDialog(null)}
            onConfirm={() =>
              remove.mutate(dashboard.id, {
                onSuccess: () => {
                  setDialog(null);
                  toast.success(`${dashboard.name} deleted`);
                  onDeleted();
                },
              })
            }
          >
            Only this arrangement goes. No data is touched. Anyone it was shared with loses it too.
          </ConfirmDialog>
        </>,
        document.body,
      )}
    </section>
  );
}

function IconButton({
  label,
  icon,
  tone,
  disabled,
  onClick,
}: {
  label: string;
  icon: string;
  tone?: "danger";
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button type="button" aria-label={label} title={label} disabled={disabled} onClick={onClick}
            className={cn(
              "grid size-[26px] place-items-center rounded-u-control border border-u-border bg-u-surface text-u-text2 transition",
              "hover:text-u-text disabled:cursor-not-allowed disabled:opacity-40",
              tone === "danger" && "hover:border-u-offlimits hover:text-u-offlimits",
            )}>
      <Icon d={icon} size={13} />
    </button>
  );
}
