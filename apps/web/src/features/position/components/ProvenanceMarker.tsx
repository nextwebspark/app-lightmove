import { useEffect, useId, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { FieldSource, ProposalConfidence } from "../api/types";

const CONFIDENCE_LABEL: Record<ProposalConfidence, string> = {
  high: "High confidence",
  medium: "Medium confidence",
  low: "Worth checking",
};

const PANEL_WIDTH = 260;
/** Kept clear of the viewport edge, and the room a panel needs below the glyph before it flips above. */
const EDGE_GAP = 12;
const PANEL_GAP = 6;

/**
 * The only visible provenance UI: a small sparkle a `DOCUMENT` value wears, nothing for `TEMPLATE` or
 * `MANUAL`. Hover or focus opens a hand-rolled popover — where the value was read from, the snippet it
 * was read out of, and an Undo.
 *
 * <p>Drawn as every other floating surface in the app is — a hairline, a panel ground and the
 * elevation ladder's top step — rather than as an inverted black tooltip, which it was alone in being.
 * The machine's voice is carried in the ink instead: the sparkle and the Undo wear `u-inferred`, the
 * same way the reading's strip and the file card's Extract with AI do.
 *
 * <p>Portalled and positioned from the trigger's measured rect, the escape {@link TruncatedText} and
 * {@link Popover} already make: the glyph sits inside a competency table that scrolls sideways, a
 * benefits table that does the same, and a React Flow canvas whose viewport is both clipped and
 * `transform`ed. An `absolute` panel is cut off by all three, and `position: fixed` alone does not
 * help inside the transformed one, since a transform makes its own containing block. The panel also
 * flips above the glyph rather than hanging off the bottom of the window.
 *
 * <p>`confidence`/`snippet`/`fileName`/`onUndo` come from the reading's receipt, which
 * {@link file://./../lib/receiptStore.ts} keeps for the tab — so a reload reads back the same popover
 * rather than degrading to a glyph with nothing behind it. A receipt can still be genuinely absent (a
 * second tab, storage the browser refuses), and the glyph then says "From the document" alone: the
 * value really did come from one, even where this tab no longer remembers which.
 */
export function ProvenanceMarker({
  source,
  confidence,
  snippet,
  fileName,
  onUndo,
  className,
}: {
  source: FieldSource | undefined;
  confidence?: ProposalConfidence;
  snippet?: string | null;
  /** The document the reading read, when this session still holds the receipt naming it. */
  fileName?: string;
  onUndo?: () => void;
  className?: string;
}) {
  const [anchor, setAnchor] = useState<DOMRect | null>(null);
  const [steppingIn, setSteppingIn] = useState(false);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  // Set while Escape hands focus back to the glyph, so the focus it takes does not reopen what Escape
  // was just pressed to dismiss. Cleared as soon as the pointer or focus genuinely arrives again.
  const dismissedRef = useRef(false);
  const panelId = useId();
  const open = anchor !== null;

  const holds = (node: EventTarget | null) =>
    node instanceof Node &&
    Boolean(triggerRef.current?.contains(node) || panelRef.current?.contains(node));

  /**
   * Whether a point still sits between the glyph and its panel — the small physical gap
   * {@link panelPositionOf}'s `PANEL_GAP` leaves between them, and the wider one either side of a
   * narrow glyph under a much wider centred panel. Nothing in the DOM occupies that gap, so a mouse
   * crossing it fires its `mouseover` on whatever page content happens to sit underneath, which reads
   * as "outside" to {@link holds} alone.
   */
  const bridges = (event: MouseEvent) => {
    const trigger = triggerRef.current?.getBoundingClientRect();
    const panel = panelRef.current?.getBoundingClientRect();
    // A rendered, visible glyph and panel always measure some area — the zero-by-zero rect jsdom
    // hands back with no layout engine behind it is not that, and trusting it here would make every
    // event count as "still bridging" rather than none.
    if (!trigger || !panel || (trigger.width === 0 && trigger.height === 0) || (panel.width === 0 && panel.height === 0)) {
      return false;
    }
    const pad = 4;
    const left = Math.min(trigger.left, panel.left) - pad;
    const right = Math.max(trigger.right, panel.right) + pad;
    const top = Math.min(trigger.top, panel.top) - pad;
    const bottom = Math.max(trigger.bottom, panel.bottom) + pad;
    return event.clientX >= left && event.clientX <= right && event.clientY >= top && event.clientY <= bottom;
  };

  /**
   * What closes the panel, all of it while it is open and none of it while it is not.
   *
   * <p>Leaving is read from where the pointer or the focus *arrived* rather than from a leave event's
   * `relatedTarget`. The panel is portalled to the body, so crossing from the glyph onto it is a leave
   * of the glyph with nothing in the DOM tree to pair it to: acting on that leave is what makes a
   * popover vanish from under the hand reaching for its Undo. A scroll or a resize closes it too —
   * the panel is placed from a rect measured once, and re-measuring it every frame buys nothing over
   * dismissing a popover whose anchor has moved.
   */
  useEffect(() => {
    if (!open) return;
    const outside = (event: Event) => {
      if (holds(event.target)) return;
      if (event instanceof MouseEvent && bridges(event)) return;
      setAnchor(null);
      setSteppingIn(false);
    };
    const dismiss = () => {
      setAnchor(null);
      setSteppingIn(false);
    };
    document.addEventListener("mouseover", outside, true);
    document.addEventListener("focusin", outside, true);
    // Capture: the box that scrolls is usually the table's or the canvas's rather than the window's.
    window.addEventListener("scroll", dismiss, true);
    window.addEventListener("resize", dismiss);
    return () => {
      document.removeEventListener("mouseover", outside, true);
      document.removeEventListener("focusin", outside, true);
      window.removeEventListener("scroll", dismiss, true);
      window.removeEventListener("resize", dismiss);
    };
  }, [open]);

  // Enter on the glyph asks for the panel's Undo, which does not exist until the panel has mounted —
  // so the ask is state the commit after that mount answers, rather than a focus call into thin air.
  useEffect(() => {
    if (!steppingIn || !open) return;
    panelRef.current?.querySelector("button")?.focus();
    setSteppingIn(false);
  }, [steppingIn, open]);

  if (source !== "DOCUMENT") return null;

  const reveal = () => {
    dismissedRef.current = false;
    setAnchor(triggerRef.current?.getBoundingClientRect() ?? null);
  };
  const close = () => {
    setAnchor(null);
    setSteppingIn(false);
  };

  return (
    <>
      <button
        ref={triggerRef}
        type="button"
        aria-label="Read from the document"
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        aria-describedby={open ? panelId : undefined}
        onMouseEnter={reveal}
        onFocus={() => {
          if (dismissedRef.current) return;
          reveal();
        }}
        // Reveal rather than toggle: a touch device never sends the mouseenter that would have opened
        // it, and on a pointer that did, closing on the click that follows the hover reads as a bug.
        onClick={reveal}
        onKeyDown={(event) => {
          if (event.key === "Escape") {
            dismissedRef.current = true;
            return close();
          }
          // The panel is portalled to the end of the body, so Tab cannot reach its Undo in document
          // order. Enter and Space open it and step into it instead, and Escape there comes back.
          if (event.key !== "Enter" && event.key !== " ") return;
          if (!onUndo) return;
          event.preventDefault();
          if (!open) reveal();
          setSteppingIn(true);
        }}
        className={cn(
          "grid size-4 flex-none place-items-center rounded-full transition",
          confidence === "low" ? "text-u-signal" : "text-u-inferred",
          className,
        )}
      >
        <Icon d={ICONS.sparkle} size={13} />
      </button>

      {anchor &&
        createPortal(
          <div
            ref={panelRef}
            id={panelId}
            role="group"
            aria-label="Document provenance"
            style={panelPositionOf(anchor)}
            onKeyDown={(event) => {
              if (event.key !== "Escape") return;
              close();
              dismissedRef.current = true;
              triggerRef.current?.focus();
            }}
            // `u-raised` rather than the `u-bg` a dropdown sits on: this panel opens *over* the
            // `u-surface` tables and cards, and on the dark theme `u-bg` is darker than they are — a
            // popover that reads as a hole punched in the page rather than as something above it.
            // Raised is a step away from both the page and the panels in either theme, which is the
            // job the token is named for, and what the competency table's own lifted row already uses.
            className="fixed z-[200] rounded-[10px] border border-u-border-strong bg-u-raised px-3 py-2.5 text-note text-u-text2 shadow-u-e3"
          >
            <span className="flex items-start gap-1.5 font-medium text-u-text">
              <Icon d={ICONS.sparkle} size={12} className="mt-px flex-none text-u-inferred" />
              <span className="min-w-0 break-words">
                {fileName ? `Read from ${fileName}` : "Read from the document"}
              </span>
            </span>
            {confidence && (
              <span className={cn("mt-1 block ps-[18px]", confidence === "low" ? "text-u-signal" : "text-u-text3")}>
                {CONFIDENCE_LABEL[confidence]}
              </span>
            )}
            {snippet && (
              <span className="mt-1.5 block border-s-2 border-u-border-strong ps-2 italic">&ldquo;{snippet}&rdquo;</span>
            )}
            {onUndo && (
              <button
                type="button"
                // A press inside the panel would otherwise blur the trigger first and close it before
                // the click lands.
                onMouseDown={(event) => event.preventDefault()}
                onClick={onUndo}
                className="mt-2 font-semibold text-u-inferred hover:underline"
              >
                Undo
              </button>
            )}
          </div>,
          document.body,
        )}
    </>
  );
}

/**
 * Under the glyph, centred on it, clamped inside the window — and above it instead whenever the panel
 * would not clear the bottom edge. Measured against a generous height rather than the panel's own,
 * which is not known until after it has been placed; over-reserving flips a little early, which reads
 * far better than a popover half off the screen.
 */
const ASSUMED_PANEL_HEIGHT = 120;

function panelPositionOf(anchor: DOMRect) {
  const flipped = anchor.bottom + PANEL_GAP + ASSUMED_PANEL_HEIGHT > window.innerHeight - EDGE_GAP;
  const left = Math.max(
    EDGE_GAP,
    Math.min(
      anchor.left + anchor.width / 2 - PANEL_WIDTH / 2,
      window.innerWidth - PANEL_WIDTH - EDGE_GAP,
    ),
  );
  return {
    left,
    width: PANEL_WIDTH,
    maxWidth: `calc(100vw - ${EDGE_GAP * 2}px)`,
    ...(flipped
      ? { bottom: Math.max(EDGE_GAP, window.innerHeight - anchor.top + PANEL_GAP) }
      : { top: anchor.bottom + PANEL_GAP }),
  };
}
