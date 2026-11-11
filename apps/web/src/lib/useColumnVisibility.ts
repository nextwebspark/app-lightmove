import { useEffect, useState } from "react";
import type { ColumnVisibilityState } from "@tanstack/react-table";

const storageKey = (namespace: string, scope: string) => `lm.${namespace}.columns.${scope}`;

/**
 * Which columns a grid shows, in `localStorage` per scope and `namespace`. A stored record is merged over the defaults,
 * so a column added later takes its default (trusting absence as visible switched every new column on for everyone).
 */
export function useColumnVisibility(
  namespace: string,
  scope: string,
  initial: ColumnVisibilityState,
) {
  const [visibility, setVisibility] = useState<ColumnVisibilityState>(() =>
    read(namespace, scope, initial),
  );

  useEffect(() => {
    try {
      localStorage.setItem(storageKey(namespace, scope), JSON.stringify(visibility));
    } catch {
      // A blocked store costs a column layout, not the table.
    }
  }, [namespace, scope, visibility]);

  return [visibility, setVisibility] as const;
}

function read(
  namespace: string,
  scope: string,
  fallback: ColumnVisibilityState,
): ColumnVisibilityState {
  try {
    const stored = localStorage.getItem(storageKey(namespace, scope));
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
