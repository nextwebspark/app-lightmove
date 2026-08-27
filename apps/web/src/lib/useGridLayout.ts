import { useEffect, useRef, useState } from "react";

/** A column as this hook needs to know it: its id, and the width it may not be dragged below. */
export interface GridLayoutColumn {
  id: string;
  min: number;
}

/** Where a grid's columns sit and how wide they are. Widths are px; absent means "take your share". */
export interface GridLayout {
  order: string[];
  widths: Record<string, number>;
}

export const EMPTY_GRID_LAYOUT: GridLayout = { order: [], widths: {} };

/** The floor a column without declared layout falls back to, both when laid out and when stored. */
export const DEFAULT_COLUMN_MIN = 96;

/** The columns a grid may remember a width for, with the floor a stored width is raised to. */
export function layoutColumnsOf(
  columns: readonly { id?: string; meta?: { min: number } }[],
): GridLayoutColumn[] {
  return columns.map((column) => ({
    id: column.id as string,
    min: column.meta?.min ?? DEFAULT_COLUMN_MIN,
  }));
}

const storageKey = (namespace: string) => `lm.${namespace}.layout`;

/**
 * How a user has dragged a grid's columns about, remembered in `localStorage`.
 *
 * <p>Per user rather than per project, unlike the visibility and sort beside it: a column layout is a
 * working habit, not a property of one mandate, so the key carries no project id. The `namespace`
 * still separates the grids — Strategy is looking at the market and Companies at what the mandate
 * took from it, and they do not hold the same columns.
 *
 * <p>A column missing from a stored order is spliced back at the index its author declared it at.
 * TanStack appends what it is not told about to the *end*, and a drop persists the whole column
 * list — so without this a column shipped in a later release would land off the right edge of the
 * grid for every user who had ever moved one.
 */
export function useGridLayout(namespace: string, columns: readonly GridLayoutColumn[]) {
  const [layout, setLayout] = useState<GridLayout>(() => read(namespace, columns));
  const stored = useRef(true);

  useEffect(() => {
    // Not on mount: the first render would write back whatever `read` returned, and a record it
    // could not parse would be overwritten with an empty layout instead of left for a later release.
    if (stored.current) {
      stored.current = false;
      return;
    }
    try {
      localStorage.setItem(storageKey(namespace), JSON.stringify(layout));
    } catch {
      // A blocked store costs a column layout, not the table.
    }
  }, [namespace, layout]);

  return [layout, setLayout] as const;
}

function read(namespace: string, columns: readonly GridLayoutColumn[]): GridLayout {
  try {
    const stored = localStorage.getItem(storageKey(namespace));
    if (!stored) return EMPTY_GRID_LAYOUT;
    const parsed: unknown = JSON.parse(stored);
    // localStorage can hold anything, including a truncated write from another release.
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
      return EMPTY_GRID_LAYOUT;
    }
    const record = parsed as { order?: unknown; widths?: unknown };
    const floors = new Map(columns.map((column) => [column.id, column.min]));
    return { order: readOrder(record.order, columns), widths: readWidths(record.widths, floors) };
  } catch {
    return EMPTY_GRID_LAYOUT;
  }
}

/**
 * The stored order, with ids the grid no longer declares dropped — a stale one would order a column
 * that cannot render — and ids it does not carry yet spliced in where they were declared.
 */
function readOrder(stored: unknown, columns: readonly GridLayoutColumn[]): string[] {
  if (!Array.isArray(stored)) return [];
  const declared = new Set(columns.map((column) => column.id));
  const seen = new Set<string>();
  const order = stored.filter(
    (id): id is string =>
      typeof id === "string" && declared.has(id) && !seen.has(id) && (seen.add(id), true),
  );
  if (order.length === 0) return order;
  columns.forEach((column, index) => {
    if (seen.has(column.id)) return;
    order.splice(Math.min(index, order.length), 0, column.id);
  });
  return order;
}

/** A width below the column's floor would break the layout's `minmax`, so it is raised rather than kept. */
function readWidths(stored: unknown, floors: Map<string, number>): Record<string, number> {
  if (typeof stored !== "object" || stored === null || Array.isArray(stored)) return {};
  const widths: Record<string, number> = {};
  for (const [id, width] of Object.entries(stored as Record<string, unknown>)) {
    const floor = floors.get(id);
    if (floor === undefined || typeof width !== "number" || !Number.isFinite(width)) continue;
    widths[id] = Math.max(floor, Math.round(width));
  }
  return widths;
}
