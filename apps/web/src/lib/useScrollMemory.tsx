import { useEffect, useLayoutEffect, type RefObject } from "react";
import { useLocation, useNavigationType } from "react-router-dom";

const positions = new Map<string, number>();

/** How long a restore keeps trying while the rows it scrolls to are still arriving. */
const RESTORE_FRAMES = 60;

/**
 * Remembers where a scroll box stood on each history entry and puts it back when Back or Forward returns there. The
 * app scrolls inside `<main>` and inside each grid, never the window, so the browser's own restoration never applies.
 * A replace (a search typed, a page turned) carries the place over to the entry that replaced it.
 */
export function useScrollMemory(ref: RefObject<HTMLElement | null>, scope: string) {
  const location = useLocation();
  const navigationType = useNavigationType();
  const key = `${location.key}:${scope}`;

  useEffect(() => {
    const box = ref.current;
    if (!box) return;
    if (navigationType === "REPLACE") positions.set(key, box.scrollTop);
    const remember = () => positions.set(key, box.scrollTop);
    box.addEventListener("scroll", remember, { passive: true });
    return () => box.removeEventListener("scroll", remember);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  useLayoutEffect(() => {
    const box = ref.current;
    const target = positions.get(key);
    if (navigationType !== "POP" || !box || !target) return;
    let frames = 0;
    let frame = 0;
    const restore = () => {
      box.scrollTop = target;
      if (Math.abs(box.scrollTop - target) > 1 && frames++ < RESTORE_FRAMES) frame = requestAnimationFrame(restore);
    };
    restore();
    return () => cancelAnimationFrame(frame);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);
}

/**
 * {@link useScrollMemory} for a shared primitive that may be drawn outside a router (a test, a dialog's portal): it
 * remembers only where there is a history to remember against.
 */
export function ScrollMemory({ target, scope }: { target: RefObject<HTMLElement | null>; scope: string }) {
  useScrollMemory(target, scope);
  return null;
}
