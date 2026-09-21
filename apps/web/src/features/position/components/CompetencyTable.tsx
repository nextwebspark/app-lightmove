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
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { IdentifiedCompetency } from "../lib/competencyRows";
import { rebalance } from "../lib/rebalance";
import { BriefButton, ColumnHead, Eyebrow, RemoveDot, StatusBadge } from "./BriefFields";

/** A few pixels before a drag starts, so a click on the handle is still a click. */
const POINTER_SENSOR = { activationConstraint: { distance: 4 } };
const KEYBOARD_SENSOR = { coordinateGetter: sortableKeyboardCoordinates };
const DRAG_MODIFIERS = [restrictToVerticalAxis, restrictToParentElement];
/** What PutCompetenciesRequest accepts; an 11th row would fail the autosave, not just the step. */
const MAX_ROWS = 10;

// Header strip and rows share one template so their columns cannot drift apart.
const ROW_GRID = "grid grid-cols-[20px_minmax(0,1.2fr)_minmax(0,1.6fr)_72px_22px_22px] items-center gap-x-3 px-4";

/**
 * One weighting panel, technical or behavioural: the competencies in rank order, what each measures,
 * and its weight.
 *
 * <p>Three things a consultant does here. Order is the ranking, so rows drag to reorder — the handle
 * is the only draggable part, since the row also carries two text inputs. A weight is typed and
 * committed on Enter or blur, and the others rebalance so the panel keeps totalling 100. A lock holds
 * one row still while the rest absorb the change — without it, the weight you had just decided is
 * the one that keeps drifting.
 */
export function CompetencyTable({
  title,
  tone,
  rows,
  locked,
  onChange,
  onToggleLock,
  onReorder,
}: {
  title: string;
  tone: "technical" | "behavioural";
  rows: IdentifiedCompetency[];
  locked: ReadonlySet<string>;
  onChange: (rows: IdentifiedCompetency[]) => void;
  onToggleLock: (id: string) => void;
  onReorder: (fromId: string, toId: string) => void;
}) {
  const total = rows.reduce((sum, row) => sum + row.weight, 0);

  // Hoisted, not inline: a fresh options object each render gives useSensor a new descriptor, which
  // hands DndContext a new sensors array and makes it re-initialise — the reorder quietly stopped
  // working after the first keystroke in a row.
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

  const handleAddRow = () =>
    onChange([...rows, { id: crypto.randomUUID(), name: "New competency", description: null, weight: 0 }]);

  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event;
    if (!over || active.id === over.id) return;
    onReorder(String(active.id), String(over.id));
  };

  return (
    <section aria-label={title}>
      <div className="mb-2 flex items-center gap-2.5">
        <span className={cn("size-2 flex-none rounded-full", tone === "technical" ? "bg-u-accent" : "bg-u-signal")} />
        <Eyebrow>{title}</Eyebrow>
        <StatusBadge tone={total === 100 ? "complete" : "offlimits"} className="ms-auto font-u-num">
          {total}%
        </StatusBadge>
      </div>

      <div className="overflow-x-auto rounded-[11px] bg-u-surface shadow-u-e1">
        <div className="min-w-[620px]">
          {rows.length > 0 && (
            <div className={cn(ROW_GRID, "border-b border-u-border py-2.5")}>
              <span />
              <ColumnHead>Competency</ColumnHead>
              <ColumnHead>Description</ColumnHead>
              <ColumnHead className="text-end">Weight</ColumnHead>
              <span />
              <span />
            </div>
          )}

          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            modifiers={DRAG_MODIFIERS}
            onDragEnd={handleDragEnd}
          >
            {/* The modifier bounds a drag to this element, so it wraps the rows alone — parenting it
                on the panel would let a row be dragged over the header. */}
            <div>
              <SortableContext items={rows.map((row) => row.id)} strategy={verticalListSortingStrategy}>
                {rows.map((row, index) => (
                  <CompetencyRow
                    key={row.id}
                    row={row}
                    index={index}
                    panelTitle={title}
                    locked={locked.has(row.id)}
                    onPatch={(changes) => patch(index, changes)}
                    onCommitWeight={(weight) => onChange(rebalance(rows, index, weight, lockedIndices))}
                    onToggleLock={() => onToggleLock(row.id)}
                    onRemove={() => onChange(rows.filter((_, i) => i !== index))}
                  />
                ))}
              </SortableContext>
            </div>
          </DndContext>

          {rows.length === 0 && (
            <p className="px-4 py-3 text-[12.5px] text-u-text3">No competencies yet.</p>
          )}
        </div>
      </div>

      {rows.length < MAX_ROWS && (
        <BriefButton variant="link" onClick={handleAddRow} className="mt-2 px-0">
          + Add competency
        </BriefButton>
      )}
    </section>
  );
}

function CompetencyRow({
  row,
  index,
  panelTitle,
  locked,
  onPatch,
  onCommitWeight,
  onToggleLock,
  onRemove,
}: {
  row: IdentifiedCompetency;
  index: number;
  panelTitle: string;
  locked: boolean;
  onPatch: (changes: Partial<IdentifiedCompetency>) => void;
  onCommitWeight: (weight: number) => void;
  onToggleLock: () => void;
  onRemove: () => void;
}) {
  const { attributes, listeners, setNodeRef, transform, transition, isDragging } = useSortable({
    id: row.id,
  });
  // The weight being typed, held apart from the row until it is committed: rebalancing the others on
  // every keystroke would move the number under the hand typing it.
  const [typed, setTyped] = useState<string | null>(null);
  // Row-qualified, always: "+ Add competency" seeds every new row with the same name, so a bare
  // row.name gives two controls in one panel the same accessible name and nothing to tell them apart.
  const named = `${row.name.trim() || "Untitled competency"} (row ${index + 1})`;

  const commitWeight = () => {
    if (typed === null) return;
    const weight = Math.max(0, Math.min(100, Math.round(Number(typed) || 0)));
    setTyped(null);
    if (weight !== row.weight) onCommitWeight(weight);
  };

  return (
    // The transform has to land on the grid row itself: a box-less wrapper measures zero to the
    // sortable strategy and takes no transform.
    <div
      ref={setNodeRef}
      style={{ transform: CSS.Transform.toString(transform), transition }}
      className={cn(
        ROW_GRID,
        "border-b border-u-border py-3",
        isDragging && "relative z-10 bg-u-raised shadow-u-e3",
        locked && "bg-u-raised/60",
      )}
    >
      <button
        type="button"
        {...attributes}
        {...listeners}
        aria-label={`Reorder ${named}`}
        title="Drag to rank, or focus and use the arrow keys"
        className="flex-none cursor-grab touch-none rounded p-0.5 text-u-text3 transition hover:text-u-text2 active:cursor-grabbing"
      >
        <Icon d={ICONS.dragHandle} size={13} />
      </button>

      <input
        value={row.name}
        aria-label={`${panelTitle} competency ${index + 1} name`}
        onChange={(event) => onPatch({ name: event.target.value })}
        className="min-w-0 bg-transparent text-[14px] font-medium text-u-text outline-none"
      />

      <input
        value={row.description ?? ""}
        aria-label={`${named} description`}
        placeholder="What this measures…"
        onChange={(event) => onPatch({ description: event.target.value || null })}
        className="min-w-0 bg-transparent text-[13px] text-u-text2 outline-none placeholder:text-u-text3/70"
      />

      <span className="flex items-center justify-end gap-0.5">
        <input
          inputMode="numeric"
          value={typed ?? String(row.weight)}
          disabled={locked}
          aria-label={`${named} weight`}
          onChange={(event) => setTyped(event.target.value.replace(/[^\d]/g, ""))}
          onBlur={commitWeight}
          onKeyDown={(event) => {
            if (event.key !== "Enter") return;
            event.preventDefault();
            commitWeight();
          }}
          className="w-9 border-b border-transparent bg-transparent text-end font-u-num text-[14px] text-u-text outline-none transition focus:border-u-accent disabled:opacity-60"
        />
        <span className="font-u-num text-[13px] text-u-text3">%</span>
      </span>

      <button
        type="button"
        aria-pressed={locked}
        aria-label={locked ? `Unlock ${named}` : `Lock ${named}`}
        title={locked ? "Unlock this weight" : "Hold this weight while the others rebalance"}
        onClick={onToggleLock}
        className={cn("flex-none rounded p-0.5 transition", locked ? "text-u-accent" : "text-u-text3/60 hover:text-u-text2")}
      >
        <Icon d={locked ? ICONS.lock : ICONS.unlock} size={13} />
      </button>

      <RemoveDot label={`Remove ${named}`} onClick={onRemove} />
    </div>
  );
}
