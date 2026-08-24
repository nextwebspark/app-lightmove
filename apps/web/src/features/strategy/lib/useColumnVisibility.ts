import { useEffect, useState } from "react";
import type { ColumnVisibilityState } from "@tanstack/react-table";

const STORAGE_PREFIX = "lm.strategy.columns.";

/**
 * Which company columns this mandate shows, remembered per project in `localStorage`.
 *
 * <p>Per project because two mandates want different columns, and local because a column tick is
 * not worth an audit event.
 *
 * <p>A stored record is merged over the defaults rather than replacing them, so a column added after
 * it was written takes its declared default. The other way round — trusting absence to mean visible —
 * meant every user who had ever opened this screen got the next release's new columns switched on.
 */
export function useColumnVisibility(projectId: string, initial: ColumnVisibilityState) {
  const [visibility, setVisibility] = useState<ColumnVisibilityState>(() =>
    read(projectId, initial),
  );

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_PREFIX + projectId, JSON.stringify(visibility));
    } catch {
      // A blocked store costs a column layout, not the table.
    }
  }, [projectId, visibility]);

  return [visibility, setVisibility] as const;
}

function read(projectId: string, fallback: ColumnVisibilityState): ColumnVisibilityState {
  try {
    const stored = localStorage.getItem(STORAGE_PREFIX + projectId);
    if (!stored) return fallback;
    const parsed: unknown = JSON.parse(stored);
    // localStorage can hold anything, including a truncated write from another release.
    if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) return fallback;
    const ticked = Object.fromEntries(
      Object.entries(parsed as Record<string, unknown>).filter(
        ([, visible]) => typeof visible === "boolean",
      ),
    ) as ColumnVisibilityState;
    return { ...fallback, ...ticked };
  } catch {
    return fallback;
  }
}
