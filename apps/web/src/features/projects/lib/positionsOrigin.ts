import { useMemo } from "react";
import { useLocation } from "react-router-dom";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";

/** The list a position was opened from, so its back link returns there — filters and all — and says so. */
export interface PositionsOrigin {
  path: string;
  label: string;
}

interface OriginState {
  positionsOrigin?: PositionsOrigin;
}

const FALLBACK: PositionsOrigin = { path: "/", label: "positions" };

const storageKey = (projectId: string) => `lightmove.positionsOrigin.${projectId}`;

/** Router state for a link that opens a position from the page this is called on. */
export function usePositionsOriginState(): OriginState {
  const { pathname, search } = useLocation();
  const { units } = useWorkspaceVocabulary();
  return useMemo(() => {
    const label = labelOf(pathname, units);
    return label ? { positionsOrigin: { path: `${pathname}${search}`, label } } : {};
  }, [pathname, search, units]);
}

/**
 * The origin of the position on screen. The opening link's state lives only on that one navigation, and moving
 * between the position's tabs drops it, so the first sight of it is kept for the tab's session, per position.
 */
export function usePositionsOrigin(projectId: string | undefined): PositionsOrigin {
  const { state } = useLocation();
  const arrived = (state as OriginState | null)?.positionsOrigin;
  return useMemo(() => {
    if (!projectId) return FALLBACK;
    if (arrived && isInApp(arrived.path)) {
      try {
        sessionStorage.setItem(storageKey(projectId), JSON.stringify(arrived));
      } catch {
        // Blocked storage only costs the origin on the next tab change.
      }
      return arrived;
    }
    try {
      const kept = JSON.parse(sessionStorage.getItem(storageKey(projectId)) ?? "null") as PositionsOrigin | null;
      if (kept && typeof kept.label === "string" && isInApp(kept.path)) return kept;
    } catch {
      // Unreadable is the same as never opened from a list.
    }
    return FALLBACK;
  }, [projectId, arrived]);
}

function labelOf(pathname: string, units: string): string | null {
  if (pathname === "/") return "My positions";
  if (pathname === "/all") return "All positions";
  if (pathname === "/clients" || pathname.startsWith("/clients/")) return units;
  return null;
}

function isInApp(path: unknown): path is string {
  return typeof path === "string" && path.startsWith("/") && !path.startsWith("//");
}
