import { DndContext, closestCenter, type DragEndEvent } from "@dnd-kit/core";
import { rectSortingStrategy, SortableContext, useSortable } from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { useState, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { ActionMenu, MenuItem, MenuSeparator } from "../../../components/ui/ActionMenu";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { cn } from "../../../lib/cn";
import { moveItem } from "../../../lib/moveItem";
import { useSortableSensors } from "../../../lib/sortableSensors";
import type { ChartQueryDef, Dashboard, DashboardScope, DashboardWidget, WidgetSize } from "../api/types";
import { NEXT_SIZE, SIZE_LABELS } from "../lib/chartRules";
import { bodyOf, useDashboardWrites } from "../lib/useDashboardWrites";
import { ChartCard } from "./ChartCard";
import { ChartLibraryDrawer, draftFor, type ChartDraft } from "./ChartLibraryDrawer";
import { DashboardNameDialog } from "./DashboardNameDialog";

export const MAX_CHARTS = 30;

const SPAN: Record<WidgetSize, string> = {
  S: "col-span-12 md:col-span-6 xl:col-span-4",
  M: "col-span-12 md:col-span-6",
  L: "col-span-12",
};

interface DraftItem {
  key: string;
  widget: DashboardWidget;
}

let keySequence = 0;
const keyed = (widget: DashboardWidget): DraftItem => ({ key: `chart-${++keySequence}`, widget });

/**
 * One custom dashboard: its charts on a twelve-column grid, and — for its owner — Edit layout (drag or move, resize,
 * remove), the chart library, and rename / duplicate / share / delete. A teammate's shared dashboard is read-only.
 */
export function DashboardView({
  scope,
  dashboard,
  library,
  liveNote,
  sharedWith,
  onOpen,
  onDeleted,
}: {
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
  const { create, save, remove } = useDashboardWrites(scope);
  const sensors = useSortableSensors();
  const [draft, setDraft] = useState<DraftItem[] | null>(null);
  const [libraryDraft, setLibraryDraft] = useState<ChartDraft | null | "library">(null);
  const [dialog, setDialog] = useState<"rename" | "delete" | null>(null);
  const editing = draft !== null;
  const items = draft ?? dashboard.widgets.map((widget, index) => ({ key: `saved-${index}`, widget }));
  const queryOf = (id: string) => library.find((query) => query.id === id);

  const startEditing = (extra: DashboardWidget[] = []) =>
    setDraft([...(draft ?? dashboard.widgets.map(keyed)), ...extra.map(keyed)]);

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
    const widget: DashboardWidget = {
      queryId: chart.query.id,
      chart: chart.chart,
      options: chart.options,
      title: chart.title.trim() || null,
      size: chart.index === null ? (chart.query.shape === "KPI" ? "S" : "M") : items[chart.index].widget.size,
    };
    if (chart.index === null) {
      if (items.length >= MAX_CHARTS) {
        toast.error(`A dashboard can hold up to ${MAX_CHARTS} charts. Remove one to add another.`);
        return;
      }
      startEditing([widget]);
      return;
    }
    if (editing) {
      setDraft(draft.map((item, index) => (index === chart.index ? { ...item, widget } : item)));
      return;
    }
    const widgets = dashboard.widgets.map((saved, index) => (index === chart.index ? widget : saved));
    save.mutate({ id: dashboard.id, body: bodyOf(dashboard, { widgets }) }, { onSuccess: () => toast.success("Chart updated") });
  };

  const handleDragEnd = (event: DragEndEvent) => {
    if (!draft || !event.over || event.active.id === event.over.id) return;
    const from = draft.findIndex((item) => item.key === event.active.id);
    const to = draft.findIndex((item) => item.key === event.over?.id);
    if (from >= 0 && to >= 0) setDraft(moveItem(draft, from, to));
  };

  const editChart = (index: number) => {
    const query = queryOf(items[index].widget.queryId);
    if (query) setLibraryDraft(draftFor(query, items[index].widget, index));
  };

  const handleDuplicate = () =>
    create.mutate(
      { name: `${dashboard.name} (copy)`.slice(0, 80), shared: false, widgets: dashboard.widgets, version: null },
      {
        onSuccess: (copy) => {
          toast.success("Dashboard duplicated");
          onOpen(copy.id);
        },
      },
    );

  return (
    <section aria-label={dashboard.name} className="animate-fade-up">
      <div className="flex flex-wrap items-start justify-between gap-x-5 gap-y-3.5">
        <div className="min-w-0">
          <h1 className="type-title">{dashboard.name}</h1>
          <p className="mt-1.5 flex items-center gap-1.5 text-note text-u-text3">
            <Icon d={dashboard.shared ? ICONS.team : ICONS.lock} size={13} />
            {!dashboard.owned
              ? `Shared by a teammate — read only · ${liveNote}`
              : dashboard.shared
                ? `Shared with ${sharedWith} · ${liveNote}`
                : `Only you can see this dashboard · ${liveNote}`}
          </p>
        </div>
        {dashboard.owned && !editing && (
          <div className="flex items-center gap-2">
            <Button variant="secondary" size="sm" onClick={() => startEditing()}>
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
          <Button variant="secondary" size="sm" loading={create.isPending} onClick={handleDuplicate}>
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
      </div>

      {editing && (
        <p className="mt-4 rounded-u-chip bg-u-accent-tint px-3.5 py-2.5 text-note text-u-text2">
          Drag a chart by its handle to move it, use its size to resize it, or the pencil to change its chart type and
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
        <DndContext sensors={sensors} collisionDetection={closestCenter} onDragEnd={handleDragEnd}>
          <SortableContext items={items.map((item) => item.key)} strategy={rectSortingStrategy}>
            <div className="mt-5 grid grid-cols-12 gap-3.5">
              {items.map((item, index) => (
                <SortableChart key={item.key} id={item.key} editing={editing} className={SPAN[item.widget.size]}
                               sizeLabel={SIZE_LABELS[item.widget.size]}
                               onResize={() => setDraft(items.map((other, position) => position === index
                                 ? { ...other, widget: { ...other.widget, size: NEXT_SIZE[other.widget.size] } }
                                 : other))}>
                  {(handle) => (
                    <ChartCard
                      scope={scope}
                      widget={item.widget}
                      query={queryOf(item.widget.queryId)}
                      className={cn(editing && "pb-10 outline-[1.5px] -outline-offset-[1.5px] outline-dashed outline-u-accent-ring")}
                      actions={
                        editing ? (
                          <>
                            {handle}
                            <IconButton label="Move earlier" icon={ICONS.arrowLeft} disabled={index === 0}
                                        onClick={() => setDraft(moveItem(items, index, index - 1))} />
                            <IconButton label="Move later" icon={ICONS.arrowRight} disabled={index === items.length - 1}
                                        onClick={() => setDraft(moveItem(items, index, index + 1))} />
                            {queryOf(item.widget.queryId) && (
                              <IconButton label="Edit chart" icon={ICONS.pencil} onClick={() => editChart(index)} />
                            )}
                            <IconButton label="Remove chart" icon={ICONS.close} tone="danger"
                                        onClick={() => setDraft(items.filter((_, other) => other !== index))} />
                          </>
                        ) : dashboard.owned && queryOf(item.widget.queryId) ? (
                          <IconButton label="Edit chart" icon={ICONS.pencil} onClick={() => editChart(index)} />
                        ) : undefined
                      }
                    />
                  )}
                </SortableChart>
              ))}
              {editing && (
                <button type="button" onClick={() => setLibraryDraft("library")}
                        className="col-span-12 flex items-center justify-center gap-2 rounded-u-card border-[1.5px] border-dashed border-u-border-strong p-5 text-body font-medium text-u-text2 hover:border-u-accent-ring hover:text-u-accent">
                  <Icon d={ICONS.plus} size={15} />
                  Add chart
                </button>
              )}
            </div>
          </SortableContext>
        </DndContext>
      )}

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
    </section>
  );
}

function SortableChart({
  id,
  editing,
  className,
  sizeLabel,
  onResize,
  children,
}: {
  id: string;
  editing: boolean;
  className: string;
  sizeLabel: string;
  onResize: () => void;
  children: (handle: ReactNode) => ReactNode;
}) {
  const sortable = useSortable({ id, disabled: !editing });
  const handle = (
    <button type="button" aria-label="Drag to move" title="Drag to move"
            className="grid size-[26px] cursor-grab place-items-center rounded-u-control text-u-text3 hover:text-u-text"
            {...sortable.attributes} {...sortable.listeners}>
      <Icon d={ICONS.dragHandle} size={15} />
    </button>
  );
  return (
    <div ref={sortable.setNodeRef}
         style={{ transform: CSS.Translate.toString(sortable.transform), transition: sortable.transition }}
         className={cn("relative min-w-0", className, sortable.isDragging && "z-10 opacity-80")}>
      {children(handle)}
      {editing && (
        <button type="button" onClick={onResize} aria-label={`${sizeLabel} — change size`} title="Change size"
                className="absolute bottom-1.5 right-1.5 flex cursor-nwse-resize items-center gap-1 rounded-sm bg-u-raised px-1.5 py-0.5 text-meta font-medium text-u-text2 hover:text-u-text">
          {sizeLabel}
          <Icon d="M21 15l-6 6M21 8 8 21" size={11} />
        </button>
      )}
    </div>
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
