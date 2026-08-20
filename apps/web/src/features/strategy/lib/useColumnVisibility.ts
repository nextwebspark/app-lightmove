import { useEffect, useState } from "react";
import type { ColumnVisibilityState } from "@tanstack/react-table";

const STORAGE_PREFIX = "lm.strategy.columns.";

/**
 * Which company columns this mandate shows, remembered per project in `localStorage`.
 *
 * <p><b>Per project, not per user.</b> A search for a CFO in Saudi utilities and one for a CTO in UAE
 * fintech want different columns, and a single shared setting would have each mandate silently
 * re-configuring the other's table.
 *
 * <p>This is presentation state and it stays local. Putting it on the server would make a column tick
 * a write that invalidates the mandate — an audit event, a version bump — for a choice that means
 * nothing to anyone but the browser making it.
 *
 * <p>Absent keys are visible: the state records the exceptions, so a column added in a later release
 * shows up rather than staying hidden behind a record written before it existed.
 */
export function useColumnVisibility(projectId: string, initial: ColumnVisibilityState) {
  const [visibility, setVisibility] = useState<ColumnVisibilityState>(() =>
    read(projectId, initial),
  );

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_PREFIX + projectId, JSON.stringify(visibility));
    } catch {
      // A full or blocked store costs the user their column layout on the next visit and nothing
      // else. Failing the render over it would cost them the table.
    }
  }, [projectId, visibility]);

  return [visibility, setVisibility] as const;
}

function read(projectId: string, fallback: ColumnVisibilityState): ColumnVisibilityState {
  try {
    const stored = localStorage.getItem(STORAGE_PREFIX + projectId);
    if (!stored) return fallback;
    const parsed: unknown = JSON.parse(stored);
    // Anything can be in localStorage — a truncated write, a key another release wrote a different
    // shape into. A malformed record falls back rather than reaching the table as visibility state.
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return fallback;
    return Object.fromEntries(
      Object.entries(parsed as Record<string, unknown>).filter(
        ([, visible]) => typeof visible === "boolean",
      ),
    ) as ColumnVisibilityState;
  } catch {
    return fallback;
  }
}
