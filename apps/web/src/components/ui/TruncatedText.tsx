import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "../../lib/cn";

const MAX_WIDTH = 380;
const EDGE_GAP = 12;
const ANCHOR_GAP = 6;

/**
 * One clipped line of text that reveals itself on hover, and only when it is actually clipped —
 * `scrollWidth > clientWidth` asks the element rather than guessing from a character count.
 *
 * <p>Portalled because the table scrolls in both axes: a bubble positioned inside it would be
 * clipped by the box it needs to escape, which looks broken rather than absent.
 */
export function TruncatedText({ value, className }: { value: string | null; className?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const [anchor, setAnchor] = useState<DOMRect | null>(null);

  useEffect(() => {
    if (!anchor) return;
    const dismiss = () => setAnchor(null);
    // Capture: the scroll is the table's box, not the window.
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
        // `min-w-0`: as a flex item, `min-width: auto` is the whole nowrap string, so `truncate`
        // never clips and the text paints over the next grid column.
        className={cn("block min-w-0 truncate", className)}
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
              // The Notes column sits at the right edge, where a left-aligned bubble would hang off-screen.
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
