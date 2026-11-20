import { useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent, type PointerEvent, type ReactNode } from "react";
import { cn } from "../../../lib/cn";
import type { WidgetLayout } from "../api/types";
import {
  bottomOf,
  clampSize,
  compact,
  GRID_COLUMNS,
  GRID_GAP_PX,
  GRID_ROW_PX,
  place,
  type SizeLimits,
} from "../lib/gridLayout";

/** Narrower than this the charts stack in reading order and the layout cannot be edited. */
const STACK_BELOW_PX = 720;
/** A press that travels less than this is a click, not a drag. */
const DRAG_THRESHOLD_PX = 4;
const EDIT_ROOM_ROWS = 3;

export interface GridItem {
  key: string;
  layout: WidgetLayout;
  limits: SizeLimits;
  label: string;
}

type Edge = "e" | "s" | "se";

interface Gesture {
  index: number;
  kind: "move" | Edge;
  pointerX: number;
  pointerY: number;
  origin: WidgetLayout;
  started: boolean;
}

/**
 * The dashboard canvas: charts on a twelve-column grid. In edit mode a chart is dragged by pressing anywhere on it
 * and resized from its right edge, bottom edge or corner, snapping to the grid within its type's limits; charts it
 * lands on are pushed down and everything slides up into empty rows, never sideways. Arrow keys move a focused chart,
 * Shift + arrows resize it.
 */
export function DashboardGrid({
  items,
  editing,
  onChange,
  children,
}: {
  items: GridItem[];
  editing: boolean;
  onChange: (layouts: WidgetLayout[]) => void;
  children: (index: number) => ReactNode;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(0);
  const [gesture, setGesture] = useState<Gesture | null>(null);
  const [preview, setPreview] = useState<WidgetLayout[] | null>(null);
  const [pointerOffset, setPointerOffset] = useState({ x: 0, y: 0 });

  useLayoutEffect(() => {
    const node = containerRef.current;
    if (!node) return;
    setWidth(node.clientWidth);
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    observer.observe(node);
    return () => observer.disconnect();
  }, []);

  const stacked = width > 0 && width < STACK_BELOW_PX;
  const canEdit = editing && !stacked;
  const layouts = items.map((item) => item.layout);
  const shown = preview ?? layouts;
  const columnPx = Math.max((width - GRID_GAP_PX * (GRID_COLUMNS - 1)) / GRID_COLUMNS, 0);
  const columnStep = columnPx + GRID_GAP_PX;
  const rowStep = GRID_ROW_PX + GRID_GAP_PX;
  const box = (rect: WidgetLayout): CSSProperties => ({
    left: rect.x * columnStep,
    top: rect.y * rowStep,
    width: rect.w * columnPx + (rect.w - 1) * GRID_GAP_PX,
    height: rect.h * GRID_ROW_PX + (rect.h - 1) * GRID_GAP_PX,
  });

  if (stacked || width === 0) {
    const order = items
      .map((item, index) => ({ item, index }))
      .sort((a, b) => a.item.layout.y - b.item.layout.y || a.item.layout.x - b.item.layout.x);
    return (
      <div ref={containerRef} className="mt-5">
        <div className="flex flex-col gap-3.5">
          {order.map(({ item, index }) => (
            <div key={item.key} className="min-w-0">{children(index)}</div>
          ))}
        </div>
      </div>
    );
  }

  const begin = (event: PointerEvent<HTMLElement>, index: number, kind: Gesture["kind"]) => {
    if (!canEdit || event.button !== 0) return;
    if (kind === "move" && (event.target as HTMLElement).closest("button, a, input, select, textarea")) return;
    event.preventDefault();
    event.stopPropagation();
    event.currentTarget.setPointerCapture(event.pointerId);
    setGesture({ index, kind, pointerX: event.clientX, pointerY: event.clientY, origin: layouts[index], started: false });
  };

  const track = (event: PointerEvent<HTMLElement>) => {
    if (!gesture) return;
    const dx = event.clientX - gesture.pointerX;
    const dy = event.clientY - gesture.pointerY;
    if (!gesture.started) {
      if (Math.hypot(dx, dy) < DRAG_THRESHOLD_PX) return;
      setGesture({ ...gesture, started: true });
    }
    const { origin, index, kind } = gesture;
    const columns = Math.round(dx / columnStep);
    const rows = Math.round(dy / rowStep);
    const limits = items[index].limits;
    const rect =
      kind === "move"
        ? clampSize({ ...origin, x: origin.x + columns, y: origin.y + rows }, limits)
        : clampSize({
            ...origin,
            w: kind === "s" ? origin.w : Math.min(origin.w + columns, GRID_COLUMNS - origin.x),
            h: kind === "e" ? origin.h : origin.h + rows,
          }, limits);
    if (kind === "move") setPointerOffset({ x: dx, y: dy });
    setPreview(place(layouts, index, rect));
  };

  const finish = () => {
    if (gesture?.started && preview) onChange(compact(preview));
    setGesture(null);
    setPreview(null);
    setPointerOffset({ x: 0, y: 0 });
  };

  const nudge = (event: KeyboardEvent<HTMLElement>, index: number) => {
    if (!canEdit || event.target !== event.currentTarget) return;
    const step = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }[event.key];
    if (!step) return;
    event.preventDefault();
    const origin = layouts[index];
    const rect = event.shiftKey
      ? { ...origin, w: Math.min(origin.w + step[0], GRID_COLUMNS - origin.x), h: origin.h + step[1] }
      : { ...origin, x: origin.x + step[0], y: origin.y + step[1] };
    onChange(compact(place(layouts, index, clampSize(rect, items[index].limits))));
  };

  const dragging = gesture?.started ? gesture.index : null;
  const rows = bottomOf(shown) + (canEdit ? EDIT_ROOM_ROWS : 0);
  const canvas: CSSProperties = {
    height: Math.max(rows * rowStep - GRID_GAP_PX, 0),
    ...(canEdit && {
      backgroundColor: "color-mix(in srgb, var(--color-u-accent) 5%, transparent)",
      backgroundImage: [
        `linear-gradient(to right, transparent ${columnPx}px, var(--color-u-surface) ${columnPx}px)`,
        `linear-gradient(to bottom, transparent ${GRID_ROW_PX}px, var(--color-u-surface) ${GRID_ROW_PX}px)`,
      ].join(", "),
      backgroundSize: `${columnStep}px 100%, 100% ${rowStep}px`,
    }),
  };

  return (
    <div ref={containerRef} className="mt-5">
      <div className="relative rounded-u-card" style={canvas}>
        {dragging !== null && (
          <div aria-hidden="true"
               className="absolute rounded-u-card border-[1.5px] border-dashed border-u-accent bg-u-accent-tint transition-all duration-150"
               style={box(shown[dragging])} />
        )}
        {items.map((item, index) => {
          const moving = dragging === index && gesture?.kind === "move";
          const resizing = dragging === index && gesture?.kind !== "move";
          const style: CSSProperties = moving
            ? { ...box(layouts[index]), transform: `translate(${pointerOffset.x}px, ${pointerOffset.y}px)` }
            : box(shown[index]);
          return (
            <div
              key={item.key}
              role={canEdit ? "group" : undefined}
              aria-roledescription={canEdit ? "movable chart" : undefined}
              aria-label={canEdit ? `${item.label} — drag to move, arrow keys to move, Shift + arrow keys to resize` : undefined}
              tabIndex={canEdit ? 0 : undefined}
              onPointerDown={(event) => begin(event, index, "move")}
              onPointerMove={track}
              onPointerUp={finish}
              onPointerCancel={finish}
              onKeyDown={(event) => nudge(event, index)}
              className={cn(
                "group/chart absolute min-w-0 rounded-u-card outline-none",
                !moving && !resizing && "transition-[left,top,width,height] duration-150 ease-out",
                canEdit && "cursor-grab touch-none select-none focus-visible:ring-2 focus-visible:ring-u-accent-ring",
                moving && "z-20 cursor-grabbing opacity-90 shadow-u-e3",
                resizing && "z-20",
              )}
              style={style}
            >
              {children(index)}
              {canEdit && (["e", "s", "se"] as const).map((edge) => (
                <span
                  key={edge}
                  aria-hidden="true"
                  onPointerDown={(event) => begin(event, index, edge)}
                  onPointerMove={track}
                  onPointerUp={finish}
                  onPointerCancel={finish}
                  className={cn(
                    "absolute z-10",
                    edge === "e" && "-right-1 top-3 bottom-3 w-2 cursor-ew-resize",
                    edge === "s" && "-bottom-1 left-3 right-3 h-2 cursor-ns-resize",
                    edge === "se" && "-bottom-1 -right-1 size-4 cursor-nwse-resize",
                  )}
                >
                  {edge === "se" && (
                    <svg viewBox="0 0 10 10" className="absolute bottom-1.5 right-1.5 size-2.5 text-u-text3 group-hover/chart:text-u-accent">
                      <path d="M9 3 3 9M9 6 6 9" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
                    </svg>
                  )}
                </span>
              ))}
            </div>
          );
        })}
      </div>
    </div>
  );
}
