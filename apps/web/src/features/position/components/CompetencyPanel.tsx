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
import { AddRowButton, RemoveRowButton } from "./fields";

/**
 * One weighting panel (technical = sky, behavioural = amber).
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
  const slider = accent === "sky" ? "accent-sky" : "accent-amber-btn";

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
    <div
      className={cn(
        "rounded-[10px] border p-4",
        accent === "sky" ? "border-sky/30 bg-sky-dim/40" : "border-amber-btn/35 bg-amber-dim/40",
      )}
    >
      <div className="mb-3.5 flex items-center justify-between">
        <span className="flex items-center gap-[7px] text-[13px] font-semibold">
          <span className={cn("size-2 rounded-full", dot)} />
          {title}
        </span>
        <span
          className={cn(
            "rounded-md px-2.5 py-0.5 font-mono text-sm font-bold",
            total === 100 ? "bg-green-dim text-green" : "bg-red-dim text-red",
          )}
        >
          {total}%
        </span>
      </div>

      <DndContext
        sensors={sensors}
        collisionDetection={closestCenter}
        modifiers={DRAG_MODIFIERS}
        onDragEnd={handleDragEnd}
      >
        <SortableContext items={rows.map((row) => row.id)} strategy={verticalListSortingStrategy}>
          {rows.map((row, index) => (
            <CompetencyRow
              key={row.id}
              row={row}
              index={index}
              panelTitle={title}
              slider={slider}
              locked={locked.has(row.id)}
              canRemove={rows.length > 1}
              onPatch={(changes) => patch(index, changes)}
              onSlide={(weight) => onChange(rebalance(rows, index, weight, lockedIndices))}
              onToggleLock={() => onToggleLock(row.id)}
              onRemove={() => onChange(rows.filter((_, i) => i !== index))}
            />
          ))}
        </SortableContext>
      </DndContext>

      <AddRowButton
        className="w-full"
        onClick={() =>
          onChange([
            ...rows,
            { id: crypto.randomUUID(), name: "New competency", description: null, weight: 0 },
          ])
        }
      >
        + Add competency
      </AddRowButton>
    </div>
  );
}

function CompetencyRow({
  row,
  index,
  panelTitle,
  slider,
  locked,
  canRemove,
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
  canRemove: boolean;
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
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn("mb-3.5 rounded-md", isDragging && "relative z-10 bg-panel/60 shadow-panel")}
    >
      <div className="mb-1.5 flex items-center gap-2">
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
          className="min-w-0 flex-1 bg-transparent text-[13px] font-medium text-text outline-none"
        />

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
          className="w-[52px] rounded-md border border-line bg-panel px-1.5 py-[3px] text-right font-mono text-xs font-semibold text-text outline-none disabled:opacity-60"
        />
        <span className="font-mono text-xs font-medium text-text3">%</span>
      </div>

      <input
        value={row.description ?? ""}
        aria-label={`${row.name} description`}
        placeholder="What this measures…"
        onChange={(e) => onPatch({ description: e.target.value || null })}
        className="mb-1.5 w-full bg-transparent font-mono text-[11.5px] text-text3 outline-none placeholder:text-text3/60"
      />

      <div className="flex items-center gap-2">
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
          className={cn("min-w-0 flex-1 disabled:opacity-50", slider)}
        />
        <button
          type="button"
          aria-pressed={locked}
          aria-label={locked ? `Unlock ${named}` : `Lock ${named}`}
          title={locked ? "Unlock this weight" : "Hold this weight while the others rebalance"}
          onClick={onToggleLock}
          className={cn(
            "flex-none rounded p-0.5 transition",
            locked ? "text-text" : "text-text3/60 hover:text-text2",
          )}
        >
          <Icon d={locked ? ICONS.lock : ICONS.unlock} size={13} />
        </button>
        {canRemove && <RemoveRowButton label={`Remove ${named}`} onClick={onRemove} />}
      </div>
    </div>
  );
}
