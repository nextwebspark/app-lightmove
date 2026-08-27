import { useEffect, useState } from "react";

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
 * <p>Only the order a user actually chose is stored. A column missing from the record keeps its
 * declared position, because TanStack appends unlisted columns in definition order — so a column
 * shipped after the record was written appears where its author put it rather than at the end.
 */
export function useGridLayout(namespace: string, columns: readonly GridLayoutColumn[]) {
  const [layout, setLayout] = useState<GridLayout>(() => read(namespace, columns));

  useEffect(() => {
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
    return { order: readOrder(record.order, floors), widths: readWidths(record.widths, floors) };
  } catch {
    return EMPTY_GRID_LAYOUT;
  }
}

/** Ids the grid no longer declares are dropped: a stale one would order a column that cannot render. */
function readOrder(stored: unknown, floors: Map<string, number>): string[] {
  if (!Array.isArray(stored)) return [];
  const seen = new Set<string>();
  return stored.filter(
    (id): id is string =>
      typeof id === "string" && floors.has(id) && !seen.has(id) && (seen.add(id), true),
  );
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
