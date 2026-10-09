import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";
import { useLocation } from "react-router-dom";
import { useEscapeKey } from "../../lib/useEscapeKey";

/**
 * A button that opens a menu of `role="menuitem"` controls: an outside click, Escape or a change of page closes it,
 * opening focuses the first item, the arrow keys, Home and End move between items, and closing from the keyboard
 * hands focus back to the button.
 */
export function useDropdownMenu() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const { pathname } = useLocation();

  const close = useCallback((returnFocus = false) => {
    setOpen(false);
    if (returnFocus) triggerRef.current?.focus();
  }, []);

  // On the shared Escape stack, so it closes even when a click left focus outside the menu, and never the panel
  // beneath it as well.
  useEscapeKey(open, () => close(true));

  useEffect(() => {
    setOpen(false);
  }, [pathname]);

  useEffect(() => {
    if (!open) return;
    const handleOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleOutside);
    menuRef.current?.querySelector<HTMLElement>('[role^="menuitem"]:not([aria-disabled="true"])')?.focus();
    return () => document.removeEventListener("mousedown", handleOutside);
  }, [open]);

  const handleMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Tab") {
      // Back to the trigger first, so Tab moves on from a node that is still in the page.
      close(true);
      return;
    }
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLElement>('[role^="menuitem"]') ?? []);
    if (items.length === 0) return;
    const at = items.indexOf(document.activeElement as HTMLElement);
    const next =
      event.key === "ArrowDown"
        ? items[(at + 1) % items.length]
        : event.key === "ArrowUp"
          ? items[(at - 1 + items.length) % items.length]
          : event.key === "Home"
            ? items[0]
            : event.key === "End"
              ? items[items.length - 1]
              : null;
    if (next) {
      event.preventDefault();
      next.focus();
    }
  };

  return {
    open,
    toggle: () => setOpen((wasOpen) => !wasOpen),
    close,
    rootRef,
    triggerRef,
    menuRef,
    triggerProps: { "aria-haspopup": "menu" as const, "aria-expanded": open },
    menuProps: { role: "menu" as const, onKeyDown: handleMenuKeyDown },
  };
}
