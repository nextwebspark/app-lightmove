import { useEffect, useMemo } from "react";
import { useLocation } from "react-router-dom";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";

/** The list a position was opened from, so its back link returns there — filters and all — and says so. */
export interface PositionsOrigin {
  path: string;
  label: string;
}

/**
 * Router state on every link that opens a position: the list's address, or null from anywhere that is not a list
 * of positions, which forgets an origin kept from an earlier visit.
 */
interface OriginState {
  positionsOrigin: string | null;
}

const HOME = "/";

const storageKey = (projectId: string) => `lightmove.positionsOrigin.${projectId}`;

/** Router state for a link that opens a position from the page this is called on. */
export function usePositionsOriginState(): OriginState {
  const { pathname, search } = useLocation();
  return useMemo(
    () => ({ positionsOrigin: listPathOf(pathname) ? `${pathname}${search}` : null }),
    [pathname, search],
  );
}

/** Router state for opening a position from somewhere that is not a list, such as a redirect after joining. */
export const NO_POSITIONS_ORIGIN: OriginState = { positionsOrigin: null };

/**
 * The origin of the position on screen. The opening link's state lives only on that one navigation, and moving
 * between the position's tabs drops it, so it is kept for the tab's session, per position, until the next opening.
 */
export function usePositionsOrigin(projectId: string | undefined): PositionsOrigin {
  const { state } = useLocation();
  const { units } = useWorkspaceVocabulary();
  const opened = (state as Partial<OriginState> | null) ?? {};
  const isOpening = "positionsOrigin" in opened;
  const arrived = isOpening && isInApp(opened.positionsOrigin) ? opened.positionsOrigin : null;

  useEffect(() => {
    if (!projectId || !isOpening) return;
    try {
      if (arrived) sessionStorage.setItem(storageKey(projectId), arrived);
      else sessionStorage.removeItem(storageKey(projectId));
    } catch {
      // Blocked storage only costs the origin on the next tab change.
    }
  }, [projectId, isOpening, arrived]);

  let path = arrived;
  if (!isOpening && projectId) {
    try {
      const kept = sessionStorage.getItem(storageKey(projectId));
      if (isInApp(kept)) path = kept;
    } catch {
      // Unreadable is the same as never opened from a list.
    }
  }
  path ??= HOME;
  return { path, label: labelOf(path, units) };
}

function listPathOf(pathname: string): boolean {
  return pathname === "/" || pathname === "/all" || pathname === "/clients";
}

function labelOf(path: string, units: string): string {
  const pathname = path.split(/[?#]/)[0];
  if (pathname === "/all") return "All positions";
  if (pathname === "/clients") return units;
  return "My positions";
}

function isInApp(path: unknown): path is string {
  return typeof path === "string" && /^\/(?![\\/])/.test(path) && listPathOf(path.split(/[?#]/)[0]);
}
