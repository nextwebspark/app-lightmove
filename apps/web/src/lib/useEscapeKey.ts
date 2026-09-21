import { useEffect } from "react";

/**
 * Dismisses a panel on Escape while it is open — and only the topmost one.
 *
 * <p><b>The stack is the point.</b> Every overlay in the app listened on `document` directly, so a
 * modal opened over a drawer fired both handlers and one Escape closed two things. That was
 * invisible while overlays were modal and mutually exclusive; the assistant panel is neither — it
 * docks beside the page precisely so a drawer can be opened from the grid while it is open, which
 * made "Escape closes the drawer *and* the panel behind it" the ordinary case.
 *
 * <p>Last registered wins, because that is what "on top" means for overlays that mount in order.
 */
const handlers: Array<() => void> = [];

let listening = false;

function handleKey(event: KeyboardEvent) {
  if (event.key !== "Escape") return;
  handlers.at(-1)?.();
}

export function useEscapeKey(active: boolean, onEscape: () => void) {
  useEffect(() => {
    if (!active) return;

    handlers.push(onEscape);
    if (!listening) {
      document.addEventListener("keydown", handleKey);
      listening = true;
    }

    return () => {
      const index = handlers.lastIndexOf(onEscape);
      if (index >= 0) handlers.splice(index, 1);
      if (handlers.length === 0 && listening) {
        document.removeEventListener("keydown", handleKey);
        listening = false;
      }
    };
  }, [active, onEscape]);
}
