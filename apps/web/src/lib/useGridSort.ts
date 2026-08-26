import { useEffect, useState } from "react";

/** Which way a sorted column runs. Mirrors the API's `direction` tokens. */
export type GridSortDirection = "asc" | "desc";

/**
 * A grid's sort, as one value. Generic over the field so each grid keeps its own allowlist: Strategy
 * can be sorted by the market's columns, Companies also by when a company entered the mandate, and
 * neither union should leak into the other.
 */
export interface GridSort<TField extends string> {
  field: TField;
  direction: GridSortDirection;
}

const storageKey = (namespace: string, projectId: string) => `lm.${namespace}.sort.${projectId}`;

/**
 * Which column a grid is sorted by, remembered per project in `localStorage`, beside the column
 * layout it belongs with. The filter is the server's and survives a navigation; a sort that resets
 * while the filter holds makes the same screen come back half-remembered.
 *
 * <p>`fields` is the allowlist, and it is checked on read for a reason: a field dropped from the API
 * since the value was written would be a 400 on every page load, from a stored preference the user
 * has no way to see or clear.
 */
export function useGridSort<TField extends string>(
  namespace: string,
  projectId: string,
  fields: readonly TField[],
  initial: GridSort<TField>,
) {
  const [sort, setSort] = useState<GridSort<TField>>(() =>
    read(namespace, projectId, fields, initial),
  );

  useEffect(() => {
    try {
      localStorage.setItem(storageKey(namespace, projectId), JSON.stringify(sort));
    } catch {
      // A blocked store costs a sort order, not the table.
    }
  }, [namespace, projectId, sort]);

  return [sort, setSort] as const;
}

function read<TField extends string>(
  namespace: string,
  projectId: string,
  fields: readonly TField[],
  fallback: GridSort<TField>,
): GridSort<TField> {
  try {
    const stored = localStorage.getItem(storageKey(namespace, projectId));
    if (!stored) return fallback;
    const parsed: unknown = JSON.parse(stored);
    if (typeof parsed !== "object" || parsed === null) return fallback;
    const { field, direction } = parsed as Partial<GridSort<TField>>;
    if (!fields.includes(field as TField)) return fallback;
    if (direction !== "asc" && direction !== "desc") return fallback;
    return { field: field as TField, direction };
  } catch {
    return fallback;
  }
}
