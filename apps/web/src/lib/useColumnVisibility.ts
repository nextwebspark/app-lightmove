import { useEffect, useState } from "react";
import type { ColumnVisibilityState } from "@tanstack/react-table";

const storageKey = (namespace: string, projectId: string) =>
  `lm.${namespace}.columns.${projectId}`;

/**
 * Which columns a grid shows, remembered per project in `localStorage`.
 *
 * <p>Per project because two mandates want different columns, and local because a column tick is
 * not worth an audit event. The `namespace` separates the grids that share this hook — Strategy is
 * looking at the market and Companies at what the mandate took from it, and a user who hides Revenue
 * on one has said nothing about the other.
 *
 * <p>A stored record is merged over the defaults rather than replacing them, so a column added after
 * it was written takes its declared default. The other way round — trusting absence to mean visible —
 * meant every user who had ever opened this screen got the next release's new columns switched on.
 */
export function useColumnVisibility(
  namespace: string,
  projectId: string,
  initial: ColumnVisibilityState,
) {
  const [visibility, setVisibility] = useState<ColumnVisibilityState>(() =>
    read(namespace, projectId, initial),
  );

  useEffect(() => {
    try {
      localStorage.setItem(storageKey(namespace, projectId), JSON.stringify(visibility));
    } catch {
      // A blocked store costs a column layout, not the table.
    }
  }, [namespace, projectId, visibility]);

  return [visibility, setVisibility] as const;
}

function read(
  namespace: string,
  projectId: string,
  fallback: ColumnVisibilityState,
): ColumnVisibilityState {
  try {
    const stored = localStorage.getItem(storageKey(namespace, projectId));
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
