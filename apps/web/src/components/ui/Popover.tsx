import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

/** Gap between the trigger's bottom edge and the panel, and the margin kept from the viewport edge. */
const PANEL_GAP = 6;
const EDGE_GAP = 12;

/**
 * A toolbar dropdown: a button, and a panel that closes on an outside click, Escape, or a scroll.
 *
 * <p>The panel's content is a function of `close`, so a menu item can act and dismiss without the
 * caller owning the open state.
 *
 * <p>Portalled to the body and positioned from the trigger's measured rect — the same escape
 * {@link TruncatedText} uses — rather than `absolute` inside the trigger's own box: several callers
 * (the grid header menu chief among them) open from inside a table that scrolls on both axes, and an
 * `absolute` panel is clipped by that scroll box the moment it would overflow it. A scroll closes
 * the panel instead of re-measuring it on every frame, since the fixed position would otherwise drift
 * away from the trigger that opened it.
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
  const [anchor, setAnchor] = useState<DOMRect | null>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const open = anchor !== null;

  useEffect(() => {
    if (!open) return;
    const close = () => setAnchor(null);
    const closeOnOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (triggerRef.current?.contains(target) || panelRef.current?.contains(target)) return;
      close();
    };
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") close();
    };
    // A scroll inside the panel itself — the checkbox list, or the panel's own overflow — must not
    // close it; only a scroll of whatever sits *behind* it (the table, the page) would strand the
    // fixed position, and that is the one this exists to answer.
    const closeOnOutsideScroll = (event: Event) => {
      if (event.target instanceof Node && panelRef.current?.contains(event.target)) return;
      close();
    };
    document.addEventListener("mousedown", closeOnOutside);
    document.addEventListener("keydown", closeOnEscape);
    // Capture: the scroll that would strand the panel is usually the table's box, not the window.
    window.addEventListener("scroll", closeOnOutsideScroll, true);
    window.addEventListener("resize", close);
    return () => {
      document.removeEventListener("mousedown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
      window.removeEventListener("scroll", closeOnOutsideScroll, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  const toggle = () => {
    setAnchor((current) => (current ? null : (triggerRef.current?.getBoundingClientRect() ?? null)));
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label={label}
        aria-expanded={open}
        onClick={toggle}
        className={triggerClassName}
      >
        {trigger(open)}
      </button>

      {anchor &&
        createPortal(
          <div
            ref={panelRef}
            style={{
              top: anchor.bottom + PANEL_GAP,
              left:
                align === "right"
                  ? Math.max(EDGE_GAP, anchor.right - width)
                  : Math.min(anchor.left, window.innerWidth - width - EDGE_GAP),
              width,
              maxWidth: "calc(100vw - 24px)",
            }}
            className="fixed z-[80] max-h-[70dvh] overflow-y-auto rounded-[10px] border border-line bg-panel p-2 shadow-panel"
          >
            {children(() => setAnchor(null))}
          </div>,
          document.body,
        )}
    </>
  );
}
