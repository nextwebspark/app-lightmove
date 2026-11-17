import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { useEscapeKey } from "../../lib/useEscapeKey";
import { AUTO_PANEL_ROOM, EDGE_GAP, verticalPlacement } from "./anchoredPlacement";

/**
 * A toolbar dropdown that closes on an outside click, Escape or scroll; content is a function of `close`. Portalled
 * and fixed from the trigger's rect (as {@link TruncatedText}), since an `absolute` panel is clipped inside a
 * scrolling table; a scroll closes it rather than letting it drift.
 */
export function Popover({
  trigger,
  triggerClassName,
  label,
  title,
  align = "left",
  placement = "below",
  width = 280,
  disabled,
  hasPopup,
  openOnMount = false,
  children,
}: {
  /** The button's contents. Receives the open state so a caret can flip. */
  trigger: (open: boolean) => ReactNode;
  triggerClassName?: string;
  /** Accessible name for the trigger, when its contents are not text enough. */
  label?: string;
  /** The trigger's hover hint. */
  title?: string;
  align?: "left" | "right";
  /** `auto` opens above a trigger too near the foot of the window to fit the panel beneath it. */
  placement?: "below" | "auto";
  /** `trigger` matches the trigger's own width, as a select's list does. */
  width?: number | "trigger";
  disabled?: boolean;
  hasPopup?: "listbox" | "menu" | "dialog";
  openOnMount?: boolean;
  children: (close: () => void) => ReactNode;
}) {
  const [anchor, setAnchor] = useState<DOMRect | null>(null);
  const [wanted, setWanted] = useState(AUTO_PANEL_ROOM);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const open = anchor !== null;

  useEffect(() => {
    if (!open) return;
    const close = () => setAnchor(null);
    const closeOnOutside = (event: MouseEvent) => {
      const target = event.target as Node;
      if (triggerRef.current?.contains(target) || panelRef.current?.contains(target) || isInLaterPanel(panelRef.current, target)) return;
      close();
    };
    // A scroll inside the panel itself — the checkbox list, or the panel's own overflow — must not
    // close it; only a scroll of whatever sits *behind* it (the table, the page) would strand the
    // fixed position, and that is the one this exists to answer.
    const closeOnOutsideScroll = (event: Event) => {
      if (event.target instanceof Node && (panelRef.current?.contains(event.target) || isInLaterPanel(panelRef.current, event.target))) return;
      close();
    };
    document.addEventListener("mousedown", closeOnOutside);
    // Capture: the scroll that would strand the panel is usually the table's box, not the window. Heard a
    // beat late, because bringing the trigger into view on open reports its own scroll after the panel is up.
    const listenForScroll = window.setTimeout(() => window.addEventListener("scroll", closeOnOutsideScroll, true), 150);
    window.addEventListener("resize", close);
    return () => {
      window.clearTimeout(listenForScroll);
      document.removeEventListener("mousedown", closeOnOutside);
      window.removeEventListener("scroll", closeOnOutsideScroll, true);
      window.removeEventListener("resize", close);
    };
  }, [open]);

  // The status picker low in the Companies grid opened beneath its trigger with only the guessed room in mind,
  // and its later stages ran off the foot of the window. Measured once it is up, so an `auto` panel takes
  // whichever side holds it — or the larger — and is not flipped while a search narrows it.
  useLayoutEffect(() => {
    const panel = panelRef.current;
    if (!anchor || !panel || placement !== "auto") return;
    const borders = panel.offsetHeight - panel.clientHeight;
    const height = panel.scrollHeight + borders;
    if (height > 0) setWanted(height);
  }, [anchor, placement]);

  // Through the overlays' one stack: inside a dialog, Escape closes the panel and leaves the dialog open.
  useEscapeKey(open, () => {
    setAnchor(null);
    triggerRef.current?.focus();
  });

  const toggle = () => {
    // A trigger half under its scroll box's edge is scrolled into view by the click's own focus, and that
    // scroll would close the panel it just opened — so it is brought in first, and measured where it lands.
    if (!open) triggerRef.current?.scrollIntoView?.({ block: "nearest", inline: "nearest" });
    setWanted(AUTO_PANEL_ROOM);
    setAnchor((current) => (current ? null : (triggerRef.current?.getBoundingClientRect() ?? null)));
  };

  useEffect(() => {
    if (openOnMount) setAnchor(triggerRef.current?.getBoundingClientRect() ?? null);
    // Once, as the line it opens is added.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const panelWidth = (box: DOMRect) => (width === "trigger" ? Math.max(box.width, 200) : width);

  const placed = (box: DOMRect) => {
    const side = verticalPlacement(box, placement, wanted);
    return { ...side, maxHeight: Math.min(side.maxHeight, window.innerHeight * 0.7) };
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label={label}
        title={title}
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
            data-popover-panel=""
            style={{
              ...placed(anchor),
              left:
                align === "right"
                  ? Math.max(EDGE_GAP, anchor.right - panelWidth(anchor))
                  : Math.max(EDGE_GAP, Math.min(anchor.left, window.innerWidth - panelWidth(anchor) - EDGE_GAP)),
              width: panelWidth(anchor),
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

/**
 * A popover opened from inside this one is portalled after it, so a click or scroll in that later panel is not outside
 * this one; one in an earlier panel (the panel this opened from) still is.
 */
function isInLaterPanel(own: HTMLElement | null, target: Node): boolean {
  const other = (target instanceof Element ? target : target.parentElement)?.closest("[data-popover-panel]");
  return Boolean(own && other && other !== own && own.compareDocumentPosition(other) & Node.DOCUMENT_POSITION_FOLLOWING);
}
