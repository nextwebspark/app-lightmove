import { type ReactNode, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { cn } from "../../lib/cn";
import { EDGE_GAP } from "./anchoredPlacement";

const DETAIL_WIDTH = 300;
const ANCHOR_GAP = 8;

/**
 * Opens an explanation on hover or focus, portalled so a scrolling drawer or dialog never clips it. It opens beneath
 * its anchor, or above it where the viewport has no room below.
 */
export function HoverDetail({
  label,
  detail,
  className = "flex-none",
  children,
}: {
  label: string;
  detail: ReactNode;
  className?: string;
  children: ReactNode;
}) {
  const ref = useRef<HTMLSpanElement>(null);
  const tipRef = useRef<HTMLSpanElement>(null);
  const [anchor, setAnchor] = useState<DOMRect | null>(null);
  const [isAbove, setIsAbove] = useState(false);
  const tipId = useId();
  const show = () => ref.current && setAnchor(ref.current.getBoundingClientRect());
  const hide = () => {
    setAnchor(null);
    setIsAbove(false);
  };

  useLayoutEffect(() => {
    const tip = tipRef.current;
    if (!anchor || !tip) return;
    const height = tip.offsetHeight;
    const fitsBelow = anchor.bottom + ANCHOR_GAP + height <= window.innerHeight - EDGE_GAP;
    const fitsAbove = anchor.top - ANCHOR_GAP - height >= EDGE_GAP;
    setIsAbove(!fitsBelow && fitsAbove);
  }, [anchor]);

  return (
    <span
      ref={ref}
      onMouseEnter={show}
      onMouseLeave={hide}
      onFocus={show}
      onBlur={hide}
      aria-describedby={anchor ? tipId : undefined}
      className={cn("inline-flex", className)}
    >
      {children}
      {anchor &&
        createPortal(
          <span
            ref={tipRef}
            id={tipId}
            role="tooltip"
            style={{
              ...(isAbove
                ? { bottom: window.innerHeight - anchor.top + ANCHOR_GAP }
                : { top: anchor.bottom + ANCHOR_GAP }),
              left: Math.max(EDGE_GAP, Math.min(anchor.left - 4, window.innerWidth - DETAIL_WIDTH - EDGE_GAP)),
              width: DETAIL_WIDTH,
            }}
            className="pointer-events-none fixed z-[200] rounded-u-chip border border-u-border-strong bg-u-surface px-3 py-2.5 text-note leading-relaxed text-u-text2 shadow-u-e3"
          >
            <span className="mb-0.5 block font-medium text-u-text">{label}</span>
            {detail}
          </span>,
          document.body,
        )}
    </span>
  );
}
