import { useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { cn } from "../../lib/cn";

/** Enough of a description to judge the company by, without the panel becoming the description. */
const DEFAULT_LINES = 5;

function clampTo(lines: number): CSSProperties {
  return {
    display: "-webkit-box",
    WebkitBoxOrient: "vertical",
    WebkitLineClamp: lines,
    overflow: "hidden",
  };
}

/**
 * A paragraph clipped to a few lines, with a toggle that appears only when there is more to read.
 *
 * <p>Whether there is more is measured from the element — `scrollHeight > clientHeight` — rather than
 * counted in characters: how many lines a description takes depends on the panel it is read in, and a
 * "Show more" that opens nothing is worse than no control at all.
 */
export function ExpandableText({
  text,
  lines = DEFAULT_LINES,
  className,
  moreLabel = "Show more",
  lessLabel = "Show less",
}: {
  text: string;
  /** Lines shown before the clip. */
  lines?: number;
  className?: string;
  moreLabel?: string;
  lessLabel?: string;
}) {
  const ref = useRef<HTMLParagraphElement>(null);
  const [expanded, setExpanded] = useState(false);
  const [clipped, setClipped] = useState(false);

  useLayoutEffect(() => {
    // With the clamp off there is nothing to learn: the element is exactly as tall as its text, so
    // re-measuring while expanded would answer "it fits" and take the toggle away mid-read.
    if (expanded) return;
    const element = ref.current;
    if (!element) return;

    // Sub-pixel line heights leave a fraction of a pixel of slack in an element that is not clipped.
    const measure = () => setClipped(element.scrollHeight > element.clientHeight + 1);
    measure();

    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [text, lines, expanded]);

  return (
    <>
      <p
        ref={ref}
        className={cn("text-[13px]/[1.6] text-text2", className)}
        style={expanded ? undefined : clampTo(lines)}
      >
        {text}
      </p>

      {clipped && (
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded((open) => !open)}
          className="mt-1.5 font-mono text-[11px] text-sky transition hover:underline"
        >
          {expanded ? lessLabel : moreLabel}
        </button>
      )}
    </>
  );
}
