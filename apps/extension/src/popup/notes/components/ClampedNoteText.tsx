// Ported from apps/web/src/features/notes/components/ClampedNoteText.tsx; keep the two in step.
import { useLayoutEffect, useRef, useState } from "react";
import { Icon } from "../../components/Icon";
import { cn } from "../../lib/cn";
import type { NoteFormat } from "../lib/noteFormat";
import { NoteText } from "./NoteText";

/** About eight lines of a card's 13px body: enough to judge a note by without one note burying the list. */
const COLLAPSED_MAX_PX = 160;
/** A note only this much over the cap is shown whole: a Show more that reveals a line is worse than none. */
const SLACK_PX = 44;

const OPEN_GLYPH = "M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7";

/**
 * A note's words held to a fixed height on a card, fading out at the cap, with Show more to open the card in place
 * and Open to read it whole elsewhere. Measured rather than counted, since a formatted note's headings and lists
 * take room no character count knows, and the rich renderer arrives after the first paint. The cap is a max-height,
 * so the content's own height reads the same open or shut and the toggle never vanishes mid-read.
 */
export function ClampedNoteText({
  body,
  format,
  className,
  onOpen,
  defaultExpanded = false,
}: {
  body: string;
  format: NoteFormat;
  className?: string;
  /** Opens the whole note in a reader; without it the card offers Show more alone. */
  onOpen?: () => void;
  defaultExpanded?: boolean;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [expanded, setExpanded] = useState(defaultExpanded);
  const [clipped, setClipped] = useState(false);

  useLayoutEffect(() => {
    const element = ref.current;
    if (!element) return;
    const measure = () => setClipped(element.scrollHeight > COLLAPSED_MAX_PX + SLACK_PX);
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [body, format]);

  useLayoutEffect(() => {
    if (defaultExpanded) setExpanded(true);
  }, [defaultExpanded]);

  const isClamped = clipped && !expanded;
  const collapse = () => {
    setExpanded(false);
    // The card's top may be far above by now; bring it back so the reader is not left mid-list.
    requestAnimationFrame(() => ref.current?.closest("li")?.scrollIntoView?.({ block: "nearest" }));
  };

  return (
    <>
      <div
        ref={ref}
        data-clamped={isClamped || undefined}
        className={cn("min-w-0 [overflow-wrap:anywhere]", isClamped && "note-clamp overflow-hidden")}
        style={isClamped ? { maxHeight: COLLAPSED_MAX_PX } : undefined}
      >
        <NoteText body={body} format={format} className={className} />
      </div>
      {clipped && (
        <div className="mt-1 flex items-center gap-3 font-mono text-meta">
          <button
            type="button"
            aria-expanded={expanded}
            onClick={expanded ? collapse : () => setExpanded(true)}
            className="font-medium text-u-accent transition hover:underline"
          >
            {expanded ? "Show less" : "Show more"}
          </button>
          {onOpen && (
            <button
              type="button"
              onClick={onOpen}
              className="ms-auto flex items-center gap-1 text-u-text3 transition hover:text-u-text"
            >
              <Icon d={OPEN_GLYPH} size={11} />
              Open
            </button>
          )}
        </div>
      )}
    </>
  );
}
