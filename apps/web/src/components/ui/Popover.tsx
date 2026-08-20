import { useEffect, useRef, useState, type ReactNode } from "react";
import { cn } from "../../lib/cn";

/**
 * A toolbar dropdown: a button, and a panel anchored under it that closes on an outside click or
 * Escape.
 *
 * <p>Extracted because the Strategy toolbar grew a second one. Two hand-rolled copies of
 * outside-click-and-Escape is how one of them ends up trapping focus or leaking a document listener
 * when its parent unmounts, and the bug shows up in whichever copy was edited last.
 *
 * <p><b>The panel's content is a function of `close`.</b> A menu item usually acts and dismisses, and
 * threading that through a controlled `open` prop would put the panel's own state in every caller.
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
