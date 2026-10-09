import { useCallback, useEffect, useRef, useState, type KeyboardEvent } from "react";

/**
 * A button that opens a menu of `role="menuitem"` controls: an outside click or Escape closes it, opening focuses
 * the first item, the arrow keys, Home and End move between items, and closing from the keyboard hands focus back
 * to the button.
 */
export function useDropdownMenu() {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);

  const close = useCallback((returnFocus = false) => {
    setOpen(false);
    if (returnFocus) triggerRef.current?.focus();
  }, []);

  useEffect(() => {
    if (!open) return;
    const handleOutside = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    document.addEventListener("mousedown", handleOutside);
    menuRef.current?.querySelector<HTMLElement>('[role="menuitem"]:not([disabled])')?.focus();
    return () => document.removeEventListener("mousedown", handleOutside);
  }, [open]);

  const handleMenuKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close(true);
      return;
    }
    if (event.key === "Tab") {
      setOpen(false);
      return;
    }
    const items = Array.from(
      menuRef.current?.querySelectorAll<HTMLElement>('[role="menuitem"]:not([disabled])') ?? [],
    );
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
