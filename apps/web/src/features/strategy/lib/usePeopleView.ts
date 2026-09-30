import { useCallback, useState } from "react";

/** How People search results are read: rows to scan, or cards to weigh one person at a time. */
export type PeopleView = "table" | "cards";

const STORAGE_KEY = "lm.strategy.people.view";

/**
 * The viewer's own habit, not a mandate's: remembered once per browser, like a grid's column layout.
 * Validated on read, so a value from another build falls back to the table rather than a blank page.
 */
export function usePeopleView() {
  const [view, setViewState] = useState<PeopleView>(read);
  const setView = useCallback((next: PeopleView) => {
    setViewState(next);
    try {
      localStorage.setItem(STORAGE_KEY, next);
    } catch {
      // A blocked store costs the remembered view, not the screen.
    }
  }, []);
  return [view, setView] as const;
}

function read(): PeopleView {
  try {
    return localStorage.getItem(STORAGE_KEY) === "cards" ? "cards" : "table";
  } catch {
    return "table";
  }
}
