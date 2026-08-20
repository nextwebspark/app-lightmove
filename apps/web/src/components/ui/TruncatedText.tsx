import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "../../lib/cn";

/** Bubble geometry, in numbers, because the position is computed rather than classed. */
const MAX_WIDTH = 380;
const EDGE_GAP = 12;
const ANCHOR_GAP = 6;

/**
 * One clipped line of text that reveals itself on hover.
 *
 * <p><b>The bubble only appears when the text is actually cut.</b> Hovering a cell that already shows
 * its whole value and getting a tooltip repeating it is noise, and in a table of thirty cells per row
 * it is constant noise. `scrollWidth > clientWidth` is the question "did the ellipsis happen", asked
 * of the element itself rather than guessed from a character count that cannot know the font.
 *
 * <p><b>It renders in a portal, not in place.</b> The company table is a scroll container in both
 * axes; an absolutely positioned bubble inside it would be clipped by the very box it needs to escape,
 * which is worse than no bubble at all — it would look broken rather than absent. Fixed positioning
 * from the element's own rect puts it over everything, and any scroll dismisses it rather than
 * letting it drift away from the cell it describes.
 *
 * <p>The full string is in the DOM either way, so a screen reader reads it whole regardless of what
 * the ellipsis does. This is a visual affordance, not an accessibility one.
 */
export function TruncatedText({ value, className }: { value: string | null; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [anchor, setAnchor] = useState<DOMRect | null>(null);

  useEffect(() => {
    if (!anchor) return;
    const dismiss = () => setAnchor(null);
    // Capture: the scroll happens on the table's own box, not on the window, and a bubble pinned to
    // a rect from before the scroll would sit beside whatever moved into that space.
    window.addEventListener("scroll", dismiss, true);
    window.addEventListener("resize", dismiss);
    return () => {
      window.removeEventListener("scroll", dismiss, true);
      window.removeEventListener("resize", dismiss);
    };
  }, [anchor]);

  const reveal = () => {
    const element = ref.current;
    if (!element || element.scrollWidth <= element.clientWidth) return;
    setAnchor(element.getBoundingClientRect());
  };

  return (
    <>
      <span
        ref={ref}
        onMouseEnter={reveal}
        onMouseLeave={() => setAnchor(null)}
        className={cn("block truncate", className)}
      >
        {value ?? "—"}
      </span>

      {anchor &&
        value &&
        createPortal(
          <div
            role="tooltip"
            style={{
              top: anchor.bottom + ANCHOR_GAP,
              // Keep the bubble on screen: the Notes column sits at the right edge, where a bubble
              // left-aligned to its cell would hang off the window.
              left: Math.max(
                EDGE_GAP,
                Math.min(anchor.left, window.innerWidth - MAX_WIDTH - EDGE_GAP),
              ),
              maxWidth: MAX_WIDTH,
            }}
            className="pointer-events-none fixed z-[200] rounded-[8px] border border-line bg-panel px-3 py-2 font-sans text-[12px] leading-relaxed text-text2 shadow-panel"
          >
            {value}
          </div>,
          document.body,
        )}
    </>
  );
}
