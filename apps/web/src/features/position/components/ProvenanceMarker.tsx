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
            className="fixed z-[200] rounded-[8px] bg-u-text px-3 py-2 text-note text-u-bg shadow-u-e3"
          >
            <span className="block font-semibold">
              {fileName ? `Read from ${fileName}` : "Read from the document"}
            </span>
            {confidence && <span className="mt-0.5 block opacity-70">{CONFIDENCE_LABEL[confidence]}</span>}
            {snippet && <span className="mt-1 block italic opacity-80">&ldquo;{snippet}&rdquo;</span>}
            {onUndo && (
              <button
                type="button"
                // A press inside the panel would otherwise blur the trigger first and close it before
                // the click lands.
                onMouseDown={(event) => event.preventDefault()}
                onClick={onUndo}
                className="mt-1.5 font-semibold underline underline-offset-2 hover:no-underline"
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
