import { useEffect, useRef, type RefObject } from "react";

/**
 * Dismisses a panel on Escape while it is open — and only the topmost one.
 *
 * <p><b>The stack is the point.</b> Every overlay in the app listened on `document` directly, so a
 * modal opened over a drawer fired both handlers and one Escape closed two things. That was
 * invisible while overlays were modal and mutually exclusive; the assistant panel is neither — it
 * docks beside the page precisely so a drawer can be opened from the grid while it is open, which
 * made "Escape closes the drawer *and* the panel behind it" the ordinary case.
 *
 * <p>Last opened wins, because that is what "on top" means. A layer keeps its place while its handler
 * changes: re-registering on every new callback once lifted a drawer above the sheet opened over it.
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
