import { useEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";

/** Gap between the trigger's bottom edge and the panel, and the margin kept from the viewport edge. */
const PANEL_GAP = 6;
const EDGE_GAP = 12;
/** Room an `auto` panel wants beneath its trigger before it opens above instead. */
const AUTO_PANEL_ROOM = 340;

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
  placement = "below",
  width = 280,
  disabled,
  hasPopup,
  children,
}: {
  /** The button's contents. Receives the open state so a caret can flip. */
  trigger: (open: boolean) => ReactNode;
  triggerClassName?: string;
  /** Accessible name for the trigger, when its contents are not text enough. */
  label?: string;
  align?: "left" | "right";
  /** `auto` opens above a trigger too near the foot of the window to fit the panel beneath it. */
  placement?: "below" | "auto";
  width?: number;
  disabled?: boolean;
  hasPopup?: "listbox" | "menu" | "dialog";
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
      if (event.key !== "Escape") return;
      close();
      triggerRef.current?.focus();
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
    // Capture: the scroll that would strand the panel is usually the table's box, not the window. Heard a
    // beat late, because bringing the trigger into view on open reports its own scroll after the panel is up.
    const listenForScroll = window.setTimeout(() => window.addEventListener("scroll", closeOnOutsideScroll, true), 150);
    window.addEventListener("resize", close);
    return () => {
      window.clearTimeout(listenForScroll);
      document.removeEventListener("mousedown", closeOnOutside);
      document.removeEventListener("keydown", closeOnEscape);
      window.removeEventListener("scroll", closeOnOutsideScroll, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  const toggle = () => {
    // A trigger half under its scroll box's edge is scrolled into view by the click's own focus, and that
    // scroll would close the panel it just opened — so it is brought in first, and measured where it lands.
    if (!open) triggerRef.current?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
    setAnchor((current) => (current ? null : (triggerRef.current?.getBoundingClientRect() ?? null)));
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-haspopup={hasPopup}
        disabled={disabled}
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
              ...verticalPlacement(anchor, placement),
              left:
                align === "right"
                  ? Math.max(EDGE_GAP, anchor.right - width)
                  : Math.max(EDGE_GAP, Math.min(anchor.left, window.innerWidth - width - EDGE_GAP)),
              width,
              maxWidth: "calc(100vw - 24px)",
            }}
            // Above the drawers (90/95) and the modal (100), below the toast (120): the tag picker in the
            // Candidates drawer opened at z-[80], behind the drawer's own scrim, so "+ Tag" showed nothing.
            className="fixed z-[110] max-h-[70dvh] overflow-y-auto rounded-[10px] border border-u-border-strong bg-u-surface p-2 shadow-u-e3"
          >
            {children(() => setAnchor(null))}
          </div>,
          document.body,
        )}
    </>
  );
}

function verticalPlacement(anchor: DOMRect, placement: "below" | "auto") {
  const below = window.innerHeight - anchor.bottom - PANEL_GAP - EDGE_GAP;
  const above = anchor.top - PANEL_GAP - EDGE_GAP;
  if (placement === "auto" && below < AUTO_PANEL_ROOM && above > below) {
    return { bottom: window.innerHeight - anchor.top + PANEL_GAP, maxHeight: above };
  }
  return { top: anchor.bottom + PANEL_GAP };
}
