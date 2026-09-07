import { useEffect, useState } from "react";

/** One page of a company list where the size is fixed. Triage's Companies pages page off this. */
export const PAGE_SIZE = 25;

/**
 * The sizes a page-size picker offers. The ceiling is the API's own `company.list.max-page-size`,
 * which refuses anything larger — an option above it would be a 400 on every page turn.
 */
export const PAGE_SIZE_OPTIONS = [25, 50, 75, 100] as const;

/** The size a picker starts on before the user chooses one. */
export const DEFAULT_PAGE_SIZE = 50;

const storageKey = (namespace: string) => `lm.${namespace}.pageSize`;

/**
 * How many rows of a list a user wants at once, remembered in `localStorage`.
 *
 * <p>Per user rather than per project, like the column layout it sits beside: how much of a list
 * someone reads at a time is a working habit, not a property of one mandate.
 *
 * <p>A stored size is checked against the offered options on read, for the reason `useGridSort`
 * checks its field — a value written by a release that offered more would be a 400 on every load,
 * from a preference the user has no way to see or clear.
 */
export function usePageSize(namespace: string) {
  const [size, setSize] = useState(() => read(namespace));

  useEffect(() => {
    try {
      localStorage.setItem(storageKey(namespace), String(size));
    } catch {
      // A blocked store costs a page size, not the table.
    }
  }, [namespace, size]);

  return [size, setSize] as const;
}

function read(namespace: string): number {
  try {
    const stored = Number(localStorage.getItem(storageKey(namespace)));
    return PAGE_SIZE_OPTIONS.includes(stored as (typeof PAGE_SIZE_OPTIONS)[number])
      ? stored
      : DEFAULT_PAGE_SIZE;
  } catch {
    return DEFAULT_PAGE_SIZE;
  }
}
