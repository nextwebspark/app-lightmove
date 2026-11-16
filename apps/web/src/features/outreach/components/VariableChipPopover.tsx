import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { Button } from "../../../components/ui";
import { EDGE_GAP, verticalPlacement } from "../../../components/ui/anchoredPlacement";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import type { VariableChip, VariableChipKind } from "../lib/variableChips";

const WIDTH = 300;

/**
 * What a press on a variable's chip opens (`Outreach.dc.html`, `tokenState` `popover` and `chip`). Filled, it says where
 * the value came from and that a template keeps the variable, and offers the value as plain text to edit, or to take
 * out. With nothing on file it asks for one to type for this email alone, or to take the variable out.
 */
export function VariableChipPopover({
  target,
  forWhom,
  onTypeValue,
  onEditAsText,
  onRemove,
  onClose,
  onEscape,
}: {
  target: { name: string; kind: VariableChipKind; chip: VariableChip; rect: DOMRect };
  forWhom: string;
  /** The words typed for this email: the variable gives way to them wherever it is used. */
  onTypeValue: (text: string) => void;
  onEditAsText: () => void;
  onRemove: () => void;
  /** Dismissed from the keyboard: the caret goes back to where the words were being written. */
  onEscape: () => void;
  onClose: () => void;
}) {
  const panelRef = useRef<HTMLDivElement>(null);
  const [typedValue, setTypedValue] = useState("");
  useEscapeKey(true, onEscape);

  useEffect(() => {
    const closeOnOutside = (event: MouseEvent) => {
      if (!(event.target instanceof Element)) return;
      if (panelRef.current?.contains(event.target)) return;
      // A press on a chip is the chip's own: it opens its popover, or closes this one if it is the one pressed.
      if (event.target.closest("[data-variable]")) return;
      onClose();
    };
    // The panel is fixed where the chip was: a scroll of whatever sits behind it, or a resize, would strand it. Heard a
    // beat late, as the shared Popover's is, since opening one brings the field into view.
    const closeOnScroll = (event: Event) => {
      if (event.target instanceof Node && panelRef.current?.contains(event.target)) return;
      onClose();
    };
    document.addEventListener("mousedown", closeOnOutside);
    const listenForScroll = window.setTimeout(() => window.addEventListener("scroll", closeOnScroll, true), 150);
    window.addEventListener("resize", onClose);
    return () => {
      window.clearTimeout(listenForScroll);
      document.removeEventListener("mousedown", closeOnOutside);
      window.removeEventListener("scroll", closeOnScroll, true);
      window.removeEventListener("resize", onClose);
    };
  }, [onClose]);

  const { rect, chip } = target;
  const place = verticalPlacement(rect, "auto", 200);
  const isBlank = chip.kind === "blank";
  const label = chip.label;
  const canUse = typedValue.trim().length > 0;

  return createPortal(
    <div
      ref={panelRef}
      role="dialog"
      aria-label={label}
      style={{ ...place, left: Math.max(EDGE_GAP, Math.min(rect.left, window.innerWidth - WIDTH - EDGE_GAP)), width: WIDTH }}
      className="fixed z-[110] overflow-y-auto rounded-u-chip border border-u-border-strong bg-u-surface p-3 shadow-u-e3"
    >
      <div className="text-note font-semibold text-u-text">{label}</div>
      {isBlank ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            if (canUse) onTypeValue(typedValue.trim());
          }}
        >
          <p className="mt-0.5 text-meta text-u-offlimits">
            Nothing on file for {forWhom}. Type it for this email, or remove it.
          </p>
          <input
            autoFocus
            value={typedValue}
            onChange={(event) => setTypedValue(event.target.value)}
            aria-label={label}
            placeholder={target.name === "location" ? "e.g. Dubai" : "Type it for this email"}
            className="mt-2 w-full rounded-u-control border border-u-border bg-u-raised px-2.5 py-1.5 text-note text-u-text outline-none focus:border-u-accent"
          />
          <div className="mt-2.5 flex items-center gap-1.5">
            <Button type="submit" size="xs" disabled={!canUse}>
              Use
            </Button>
            <Button type="button" variant="ghost" size="xs" onClick={onRemove}>
              Remove from email
            </Button>
          </div>
        </form>
      ) : (
        <>
          <p className="mt-0.5 text-meta text-u-text3">
            {chip.kind === "fallback"
              ? `Nothing on file for ${forWhom}: the fallback stands in.`
              : target.name === "bookingLink"
                ? "Made for you when it is sent."
                : target.name === "senderFirstName"
                  ? "From your profile:"
                  : `From ${forWhom}'s profile:`}{" "}
            {chip.kind === "value" && target.name !== "bookingLink" && <span className="text-u-text">{chip.text}</span>}
          </p>
          <p className="mt-2 text-meta text-u-text3">
            Save as template keeps it a variable, so it fills in for whoever you write to next.
          </p>
          <div className="mt-2.5 flex items-center gap-1.5">
            {target.name !== "bookingLink" && (
              <Button type="button" variant="secondary" size="xs" onClick={onEditAsText}>
                Edit as text
              </Button>
            )}
            <Button type="button" variant="ghost" size="xs" onClick={onRemove}>
              Remove
            </Button>
          </div>
        </>
      )}
    </div>,
    document.body,
  );
}
