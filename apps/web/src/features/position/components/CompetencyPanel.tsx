import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from "@dnd-kit/core";
import { restrictToParentElement, restrictToVerticalAxis } from "@dnd-kit/modifiers";
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { IdentifiedCompetency } from "../lib/competencyRows";
import { rebalance } from "../lib/rebalance";
import { AddRowButton, ColumnLabel, RemoveRowButton } from "./fields";

/**
 * One weighting panel (technical = sky, behavioural = amber), rendered as a full-width table.
 *
 * <p>Three things a consultant does here. A slider rebalances the others so the total holds; the
 * number input sets a weight exactly; and a lock holds one row still while the rest absorb the
 * change — without it, the weight you had just decided is the one that keeps drifting.
 *
 * <p>Order is the ranking, so rows drag to reorder. The handle is the only draggable part: the row
 * also carries two text inputs and a slider, and a whole-row drag would fight all three.
 */
/** A few pixels before a drag starts, so a click on the handle is still a click. */
const POINTER_SENSOR = { activationConstraint: { distance: 4 } };
const KEYBOARD_SENSOR = { coordinateGetter: sortableKeyboardCoordinates };
const DRAG_MODIFIERS = [restrictToVerticalAxis, restrictToParentElement];
/** What PutCompetenciesRequest accepts; an 11th row would fail the autosave, not just the step. */
const MAX_ROWS = 10;

// Header strip and rows share one template so their columns cannot drift apart. Below md the row
// wraps to three lines — a 390px viewport cannot hold seven columns without scrolling sideways.
const ROW_GRID =
  "grid grid-cols-[20px_minmax(0,1fr)_74px_24px_24px] gap-x-3.5 gap-y-1 " +
  "md:grid-cols-[20px_minmax(0,1.3fr)_minmax(0,1fr)_minmax(150px,1.7fr)_74px_24px_24px]";
/** Cells that drop to their own line below md and rejoin the row at md. */
const WRAPS = "col-start-2 col-span-4 md:col-auto md:col-span-1 md:row-auto";

export function CompetencyPanel({
  title,
  accent,
  rows,
  locked,
  onChange,
  onToggleLock,
  onReorder,
}: {
  title: string;
  accent: "sky" | "amber";
  rows: IdentifiedCompetency[];
  locked: ReadonlySet<string>;
  onChange: (rows: IdentifiedCompetency[]) => void;
  onToggleLock: (id: string) => void;
  onReorder: (fromId: string, toId: string) => void;
}) {
  const total = rows.reduce((sum, row) => sum + row.weight, 0);
  const dot = accent === "sky" ? "bg-sky" : "bg-amber-btn";
  const slider = accent === "sky" ? "" : "[--range-accent:var(--color-amber-btn)]";
  const balanced =
    accent === "sky" ? "border-sky/40 bg-sky-dim text-sky" : "border-amber-btn/45 bg-amber-dim text-amber";

  // Hoisted, not inline: a fresh options object each render gives useSensor a new descriptor, which
  // hands DndContext a new sensors array and makes it re-initialise. Every keystroke on a weight
  // slider re-renders this panel, and the reorder quietly stopped working after the first one.
  const sensors = useSensors(
    useSensor(PointerSensor, POINTER_SENSOR),
    useSensor(KeyboardSensor, KEYBOARD_SENSOR),
  );

  const patch = (index: number, changes: Partial<IdentifiedCompetency>) =>
    onChange(rows.map((row, i) => (i === index ? { ...row, ...changes } : row)));

  /** The maths is index-based; the locks are by id, because indices move when rows do. */
  const lockedIndices = new Set(
    rows.map((row, index) => (locked.has(row.id) ? index : -1)).filter((index) => index >= 0),
  );

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    onReorder(String(active.id), String(over.id));
  };

  return (
    <section
      aria-label={title}
      className={cn(
        "overflow-hidden rounded-[10px] border bg-panel2",
        accent === "sky" ? "border-sky/30" : "border-amber-btn/35",
      )}
    >
      <div className="flex flex-wrap items-center gap-x-2.5 gap-y-1 border-b border-line-soft px-4 py-3">
        <span className={cn("size-2 flex-none rounded-full", dot)} />
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.1em] text-text2">
          {title}
        </span>
        <span className="text-[12.5px] text-text3">Weighted — must total 100%</span>
        <span
          className={cn(
            "ms-auto rounded-md border px-2.5 py-[3px] font-mono text-xs font-bold",
            total === 100 ? balanced : "border-red/40 bg-red-dim text-red",
          )}
        >
          {total}%
        </span>
      </div>

      {rows.length > 0 && (
        <div
          className={cn(
            ROW_GRID,
            "hidden border-b border-line-soft px-4 py-[9px] md:grid",
          )}
        >
          <span />
          <ColumnLabel>Competency</ColumnLabel>
          <ColumnLabel>Description</ColumnLabel>
          <span />
          <ColumnLabel className="text-end">Weight</ColumnLabel>
        </div>
      )}

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={DRAG_MODIFIERS}
        onDragEnd={handleDragEnd}
      >
        {/* The modifier bounds a drag to this element, so it wraps the rows alone — parenting it on
            the panel would let a row be dragged over the header and the add button. */}
        <div>
          <SortableContext items={rows.map((row) => row.id)} strategy={verticalListSortingStrategy}>
            {rows.map((row, index) => (
              <CompetencyRow
                key={row.id}
                row={row}
                index={index}
                panelTitle={title}
                slider={slider}
                locked={locked.has(row.id)}
                onPatch={(changes) => patch(index, changes)}
                onSlide={(weight) => onChange(rebalance(rows, index, weight, lockedIndices))}
                onToggleLock={() => onToggleLock(row.id)}
                onRemove={() => onChange(rows.filter((_, i) => i !== index))}
              />
            ))}
          </SortableContext>
        </div>
      </DndContext>

      {rows.length === 0 && (
        <p className="border-b border-line-soft px-4 py-3 text-[12.5px] text-text3">
          No competencies yet.
        </p>
      )}

      {rows.length < MAX_ROWS && (
        <div className="px-4 py-2.5">
          <AddRowButton onClick={addRow}>+ Add competency</AddRowButton>
        </div>
      )}
    </section>
  );

  function addRow() {
    onChange([
      ...rows,
      { id: crypto.randomUUID(), name: "New competency", description: null, weight: 0 },
    ]);
  }
}

function CompetencyRow({
  row,
  index,
  panelTitle,
  slider,
  locked,
  onPatch,
  onSlide,
  onToggleLock,
  onRemove,
}: {
  row: IdentifiedCompetency;
  index: number;
  panelTitle: string;
  slider: string;
  locked: boolean;
  onPatch: (changes: Partial<IdentifiedCompetency>) => void;
  onSlide: (weight: number) => void;
  onToggleLock: () => void;
  onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: row.id,
  });
  const named = row.name.trim() || `competency ${index + 1}`;

  return (
    // The transform lands on the grid row itself. Wrapping the row in a display:contents element
    // instead would generate no box, so the transform would be a no-op and the sortable strategy
    // would measure the row as zero-height.
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        ROW_GRID,
        "items-center border-b border-line-soft px-4 py-2.5",
        isDragging && "relative z-10 bg-panel shadow-panel",
      )}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label={`Reorder ${named}`}
        title="Drag to rank, or focus and use the arrow keys"
        className="flex-none cursor-grab touch-none rounded p-0.5 text-text3 transition hover:text-text2 active:cursor-grabbing"
      >
        <Icon d={ICONS.dragHandle} size={13} />
      </button>

      <input
        value={row.name}
        aria-label={`${panelTitle} competency ${index + 1} name`}
        onChange={(e) => onPatch({ name: e.target.value })}
        className="min-w-0 bg-transparent text-[13px] font-semibold text-text outline-none"
      />

      <input
        value={row.description ?? ""}
        aria-label={`${row.name} description`}
        placeholder="What this measures…"
        onChange={(e) => onPatch({ description: e.target.value || null })}
        className={cn(
          WRAPS,
          "row-start-2 min-w-0 bg-transparent text-[12.5px] text-text3 outline-none placeholder:text-text3/60",
        )}
      />

      {/* Disabled rather than merely ignored: dragging a row you locked contradicts the lock, and a
          slider that silently refuses to move reads as broken. */}
      <input
        type="range"
        min={0}
        max={100}
        value={row.weight}
        disabled={locked}
        aria-label={`${row.name} slider`}
        onChange={(e) => onSlide(Number(e.target.value))}
        className={cn(WRAPS, "row-start-3 w-full min-w-0 disabled:opacity-50", slider)}
      />

      <span className="col-start-3 row-start-1 flex items-center justify-end gap-1 md:col-auto md:row-auto">
        <input
          type="number"
          min={0}
          max={100}
          value={row.weight}
          disabled={locked}
          aria-label={`${row.name} weight`}
          onChange={(e) => {
            const weight = Math.max(0, Math.min(100, Math.round(Number(e.target.value) || 0)));
            onPatch({ weight });
          }}
          className="w-12 rounded-md border border-line bg-panel px-1.5 py-[3px] text-right font-mono text-xs font-semibold text-text outline-none disabled:opacity-60"
        />
        <span className="font-mono text-xs font-medium text-text3">%</span>
      </span>

      <button
        type="button"
        aria-pressed={locked}
        aria-label={locked ? `Unlock ${named}` : `Lock ${named}`}
        title={locked ? "Unlock this weight" : "Hold this weight while the others rebalance"}
        onClick={onToggleLock}
        className={cn(
          "col-start-4 row-start-1 flex-none justify-self-center rounded p-0.5 transition md:col-auto md:row-auto",
          locked ? "text-text" : "text-text3/60 hover:text-text2",
        )}
      >
        <Icon d={locked ? ICONS.lock : ICONS.unlock} size={13} />
      </button>

      <span className="col-start-5 row-start-1 justify-self-center md:col-auto md:row-auto">
        <RemoveRowButton label={`Remove ${named}`} onClick={onRemove} />
      </span>
    </div>
  );
}
