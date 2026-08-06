import type { ColumnVisibilityState } from "@tanstack/react-table";
import { useCallback, useEffect, useState } from "react";
import { DEFAULT_COLUMN_VISIBILITY } from "../components/columns";

const STORAGE_PREFIX = "lightmove.sourcing.columns";

const storageKey = (projectId: string) => `${STORAGE_PREFIX}.${projectId}`;

/**
 * Remembered per project — a tech search and a CFO search want different columns, so one setting for
 * the whole workspace would be re-picked on every switch. localStorage rather than the server: it is a
 * display preference, and nothing else in the app stores per-user UI state yet.
 */
export function useColumnVisibility(projectId: string) {
  const [visibility, setVisibility] = useState<ColumnVisibilityState>(() => storedOrDefault(projectId));

  // Switching mandates without remounting must swap layouts, not carry the previous one across.
  useEffect(() => {
    setVisibility(storedOrDefault(projectId));
  }, [projectId]);

  // Written where the change happens rather than in an effect on `visibility`: an effect would also
  // fire on the switch above, while it still closed over the previous project's layout, and store that
  // under the new project's key.
  const update = useCallback(
    (updater: ColumnVisibilityState | ((old: ColumnVisibilityState) => ColumnVisibilityState)) =>
      setVisibility((current) => {
        const next = typeof updater === "function" ? updater(current) : updater;
        try {
          localStorage.setItem(storageKey(projectId), JSON.stringify(next));
        } catch {
          // As above: an unwritable storage costs persistence, not the interaction.
        }
        return next;
      }),
    [projectId],
  );

  const reset = useCallback(() => update(DEFAULT_COLUMN_VISIBILITY), [update]);

  return { visibility, setVisibility: update, reset };
}

/** Only keys the table still declares are honoured: a column renamed or dropped in a later release
 *  must not strand a returning user with a layout the table can't satisfy. */
function storedOrDefault(projectId: string): ColumnVisibilityState {
  try {
    const raw = localStorage.getItem(storageKey(projectId));
    if (!raw) {
      return DEFAULT_COLUMN_VISIBILITY;
    }
    const stored = JSON.parse(raw) as Record<string, unknown>;
    const merged: ColumnVisibilityState = { ...DEFAULT_COLUMN_VISIBILITY };
    for (const columnId of Object.keys(DEFAULT_COLUMN_VISIBILITY)) {
      if (typeof stored[columnId] === "boolean") {
        merged[columnId] = stored[columnId];
      }
    }
    return merged;
  } catch {
    return DEFAULT_COLUMN_VISIBILITY;
  }
}
