import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { FieldSource, ProposalConfidence } from "../api/types";

const CONFIDENCE_LABEL: Record<ProposalConfidence, string> = {
  high: "High",
  medium: "Medium",
  low: "Check this",
};

/**
 * The only visible provenance UI: a small sparkle a `DOCUMENT` value wears, nothing for `TEMPLATE` or
 * `MANUAL`. Hover or focus opens a hand-rolled popover — the snippet the document read it from, and an
 * Undo — rather than a library, matching the field kit's own primitives.
 *
 * `confidence`/`snippet`/`onUndo` are session state (the receipt lib/documentFill.ts hands the wizard
 * after a reading): absent once the page has reloaded, since nothing of a reading survives that but the
 * persisted `source` itself. The glyph still shows then — "From the document" alone, no confidence, no
 * Undo — because the value really did come from a document, even if the session forgot the details.
 */
export function ProvenanceMarker({
  source,
  confidence,
  snippet,
  onUndo,
  className,
}: {
  source: FieldSource | undefined;
  confidence?: ProposalConfidence;
  snippet?: string | null;
  onUndo?: () => void;
  className?: string;
}) {
  const [open, setOpen] = useState(false);
  if (source !== "DOCUMENT") return null;

  return (
    // Hover is tracked on this wrapper, not the button alone: the popover panel is the button's
    // sibling, not its descendant, so leaving the button to move the pointer down into the panel
    // would otherwise fire the button's own mouseleave first and close it before Undo is reachable.
    <span
      className={cn("relative inline-flex", className)}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
    >
      <button
        type="button"
        aria-label="Read from the document"
        aria-expanded={open}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(event) => {
          if (event.key === "Escape") setOpen(false);
        }}
        className={cn(
          "grid size-4 flex-none place-items-center rounded-full transition",
          confidence === "low" ? "text-u-signal" : "text-u-inferred",
        )}
      >
        <Icon d={ICONS.sparkle} size={13} />
      </button>

      {open && (
        <div
          role="tooltip"
          className="absolute start-1/2 top-full z-20 mt-1.5 w-max max-w-[240px] -translate-x-1/2 rounded-[8px] bg-u-text px-3 py-2 text-[11.5px] leading-[1.5] text-u-bg shadow-u-e3"
        >
          <span className="block font-semibold">
            From the document{confidence ? ` · ${CONFIDENCE_LABEL[confidence]}` : ""}
          </span>
          {snippet && <span className="mt-1 block italic opacity-80">&ldquo;{snippet}&rdquo;</span>}
          {onUndo && (
            <button
              type="button"
              // A press inside the popover would otherwise blur the trigger first and close it before
              // the click lands.
              onMouseDown={(event) => event.preventDefault()}
              onClick={onUndo}
              className="mt-1.5 font-semibold underline underline-offset-2 hover:no-underline"
            >
              Undo
            </button>
          )}
        </div>
      )}
    </span>
  );
}
