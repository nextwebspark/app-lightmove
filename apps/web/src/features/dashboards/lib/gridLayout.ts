import type { ChartType, WidgetLayout } from "../api/types";

export const GRID_COLUMNS = 12;
export const GRID_ROW_PX = 44;
export const GRID_GAP_PX = 14;
export const GRID_MAX_HEIGHT = 24;

export interface SizeLimits {
  minW: number;
  minH: number;
  maxW: number;
  maxH: number;
}

/** The smallest a chart of each type still reads at, and the size a new one starts at. */
const LIMITS: Record<ChartType, { minW: number; minH: number; w: number; h: number }> = {
  kpi: { minW: 2, minH: 3, w: 3, h: 3 },
  bar: { minW: 3, minH: 5, w: 6, h: 7 },
  column: { minW: 3, minH: 5, w: 6, h: 7 },
  pie: { minW: 4, minH: 5, w: 4, h: 6 },
  donut: { minW: 4, minH: 5, w: 4, h: 6 },
  line: { minW: 4, minH: 5, w: 6, h: 6 },
  area: { minW: 4, minH: 5, w: 6, h: 6 },
  stacked: { minW: 4, minH: 5, w: 6, h: 7 },
  table: { minW: 4, minH: 4, w: 6, h: 7 },
};

export function limitsFor(chart: ChartType): SizeLimits {
  const { minW, minH } = LIMITS[chart];
  return { minW, minH, maxW: GRID_COLUMNS, maxH: GRID_MAX_HEIGHT };
}

export function clampSize(rect: WidgetLayout, limits: SizeLimits): WidgetLayout {
  const w = Math.min(Math.max(rect.w, limits.minW), limits.maxW);
  const h = Math.min(Math.max(rect.h, limits.minH), limits.maxH);
  return { x: Math.min(Math.max(rect.x, 0), GRID_COLUMNS - w), y: Math.max(rect.y, 0), w, h };
}

function overlaps(a: WidgetLayout, b: WidgetLayout): boolean {
  return a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h;
}

export function bottomOf(rects: WidgetLayout[]): number {
  return rects.reduce((bottom, rect) => Math.max(bottom, rect.y + rect.h), 0);
}

/**
 * Gravity: every chart slides straight up into empty rows above it, keeping its column — a chart is never sent to
 * another row or column on its own. `pinned` is held where it is (the one being dragged) and the rest settle around it.
 */
export function compact(rects: WidgetLayout[], pinned: number | null = null): WidgetLayout[] {
  const settled: (WidgetLayout | null)[] = rects.map((rect, index) => (index === pinned ? { ...rect } : null));
  const order = rects
    .map((rect, index) => ({ rect, index }))
    .filter(({ index }) => index !== pinned)
    .sort((a, b) => a.rect.y - b.rect.y || a.rect.x - b.rect.x);
  for (const { rect, index } of order) {
    const placed = settled.filter((other): other is WidgetLayout => other !== null);
    let y = rect.y;
    while (placed.some((other) => overlaps({ ...rect, y }, other))) {
      y = Math.max(...placed.filter((other) => overlaps({ ...rect, y }, other)).map((other) => other.y + other.h));
    }
    // Rise one row at a time from where it sits: jumping from row 0 would lift it through a chart into a gap above.
    while (y > 0 && !placed.some((other) => overlaps({ ...rect, y: y - 1 }, other))) y--;
    settled[index] = { ...rect, y };
  }
  return settled as WidgetLayout[];
}

/**
 * One chart moved or resized to `rect`; anything it lands on is pushed down below it, then everything settles. A chart
 * moved down onto one that was below it trades places with it instead: pushed down, that chart would leave the moved
 * one an empty row to rise back into, and the drop would snap back to where it started.
 */
export function place(rects: WidgetLayout[], index: number, rect: WidgetLayout): WidgetLayout[] {
  const origin = rects[index];
  const next = rects.map((other, position) => (position === index ? rect : other));
  if (rect.y > origin.y) {
    next.forEach((other, position) => {
      if (position === index || other.y <= origin.y || !overlaps(rect, other)) return;
      const above = { ...other, y: rect.y - other.h };
      const blocked = above.y < 0
        || next.some((third, at) => at !== index && at !== position && overlaps(above, third));
      if (!blocked) next[position] = above;
    });
  }
  return compact(next, index);
}

/**
 * One chart one row down. Gravity would slide a plain step straight back, so it trades places with the nearest chart
 * below it in its columns instead; with nothing below, it stays.
 */
export function moveDown(rects: WidgetLayout[], index: number): WidgetLayout[] {
  const rect = rects[index];
  const below = rects
    .map((other, position) => ({ other, position }))
    .filter(({ other, position }) => position !== index && other.y >= rect.y + rect.h
      && other.x < rect.x + rect.w && rect.x < other.x + other.w)
    .sort((a, b) => a.other.y - b.other.y || a.other.x - b.other.x)[0];
  if (!below) return rects;
  const swapped = rects.map((other, position) => {
    if (position === below.position) return { ...other, y: rect.y };
    if (position === index) return { ...other, y: rect.y + below.other.h };
    return other;
  });
  return compact(swapped);
}

/** Where a new chart goes: the first gap at the foot of the dashboard wide enough for it. */
export function nextSlot(rects: WidgetLayout[], w: number, h: number): WidgetLayout {
  const floor = Math.max(0, ...rects.map((rect) => rect.y));
  let best: WidgetLayout = { x: 0, y: bottomOf(rects), w, h };
  for (let x = 0; x + w <= GRID_COLUMNS; x++) {
    let y = floor;
    while (rects.some((other) => overlaps({ x, y, w, h }, other))) y++;
    if (y < best.y) best = { x, y, w, h };
  }
  return best;
}

const LEGACY_WIDTH: Record<string, number> = { S: 4, M: 6, L: 12 };

/** Charts saved before the grid carry a size token and no layout: flow them left to right, row by row. */
export function withLayouts<T extends { chart: ChartType; layout: WidgetLayout | null; size?: string | null }>(
  widgets: T[],
): T[] {
  if (widgets.every((widget) => widget.layout)) return widgets;
  const rects: WidgetLayout[] = [];
  return widgets.map((widget) => {
    if (widget.layout) {
      rects.push(widget.layout);
      return widget;
    }
    const limits = LIMITS[widget.chart] ?? LIMITS.bar;
    const w = Math.max(LEGACY_WIDTH[widget.size ?? ""] ?? limits.w, limits.minW);
    const layout = nextSlot(rects, w, limits.h);
    rects.push(layout);
    return { ...widget, layout, size: null };
  });
}

/** Every chart with a place, flowed in and settled: what the screen draws and what a save sends. */
export function settledWidgets<T extends { chart: ChartType; layout: WidgetLayout | null; size?: string | null }>(
  widgets: T[],
): (T & { layout: WidgetLayout })[] {
  const laid = withLayouts(widgets) as (T & { layout: WidgetLayout })[];
  const settled = compact(laid.map((widget) => widget.layout));
  return laid.map((widget, index) => ({ ...widget, layout: settled[index] }));
}

export function startingLayout(rects: WidgetLayout[], chart: ChartType): WidgetLayout {
  const { w, h } = LIMITS[chart];
  return nextSlot(rects, w, h);
}
