import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * A toolbar dropdown: a button, and a panel that closes on an outside click or Escape.
 *
 * <p>The panel's content is a function of `close`, so a menu item can act and dismiss without the
 * caller owning the open state.
 */
export function Popover({
  trigger,
  triggerClassName,
  label,
  align = "left",
  width = 280,
  children,
}: {
  /** The button's contents. Receives the open state so a caret can flip. */
  trigger: (open: boolean) => ReactNode;
  triggerClassName?: string;
  /** Accessible name for the trigger, when its contents are not text enough. */
  label?: string;
  align?: "left" | "right";
  width?: number;
  children: (close: () => void) => ReactNode;
}) {
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!ref.current?.contains(event.target as Node)) setOpen(false);
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    document.addEventListener("mousedown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    return () => {
      document.removeEventListener("mousedown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
    };
  }, [open]);

  return (
    <div className="relative" ref={ref}>
      <button
        type="button"
        aria-label={label}
        aria-expanded={open}
        onClick={() => setOpen((isOpen) => !isOpen)}
        className={triggerClassName}
      >
        {trigger(open)}
      </button>

      {open && (
        <div
          style={{ width }}
          className={cn(
            "absolute top-9 z-[80] rounded-[10px] border border-line bg-panel p-2 shadow-panel",
            align === "right" ? "right-0" : "left-0",
          )}
        >
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}
