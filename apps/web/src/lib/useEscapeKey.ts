import { useEffect, useRef, type RefObject } from "react";

/**
 * Dismisses only the topmost open panel on Escape: the docked assistant panel made one Escape closing two layers the
 * ordinary case. Last opened wins; a layer keeps its place when its handler changes (re-registering once lifted a
 * drawer above the sheet opened over it).
 */
const handlers: Array<RefObject<() => void>> = [];

let listening = false;

function handleKey(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  handlers.at(-1)?.current();
}

export function useEscapeKey(active: boolean, onEscape: () => void) {
  const handler = useRef(onEscape);
  useEffect(() => {
    handler.current = onEscape;
  });

  useEffect(() => {
    if (!active) return;

    handlers.push(handler);
    if (!listening) {
      document.addEventListener("keydown", handleKey);
      listening = true;
    }

    return () => {
      const index = handlers.lastIndexOf(handler);
      if (index >= 0) handlers.splice(index, 1);
      if (handlers.length === 0 && listening) {
        document.removeEventListener("keydown", handleKey);
        listening = false;
      }
    };
  }, [active]);
}
