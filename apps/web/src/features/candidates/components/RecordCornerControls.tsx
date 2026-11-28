import { useContext, useEffect, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { EscapeLayerContext } from "../../../lib/useEscapeKey";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";

/**
 * How the executive record moves along the list it was opened from, and between its two frames: the peek over the
 * grid and the full page with the list beside it. Absent where the record stands alone (a report figure, a bell row).
 */
export interface RecordStepper {
  onPrevious?: () => void;
  onNext?: () => void;
  frame: "peek" | "page";
  /** Where the open person sits in the list, 0-based, for the full page's "3 of 48". */
  position?: { index: number; total: number };
  lead?: ReactNode;
  /** The peek's Open full page, the page's way back to where it was opened from; absent where there is none. */
  onToggleFrame?: () => void;
  toggleLabel?: string;
  /** The full page's record bar, where these controls draw instead of the header's corner; null until it mounts. */
  bar?: { slot: HTMLElement | null };
}

function inRecordBar(
  stepper: RecordStepper | undefined,
): stepper is RecordStepper & { bar: { slot: HTMLElement | null } } {
  return stepper?.frame === "page" && stepper.bar !== undefined;
}

function stepsShown(stepper: RecordStepper): boolean {
  return stepper.position !== undefined || stepper.onPrevious !== undefined || stepper.onNext !== undefined;
}

export const CORNER_BUTTON =
  "rounded-md p-1.5 text-u-text3 transition hover:bg-u-raised hover:text-u-text disabled:pointer-events-none disabled:opacity-40";

/**
 * The record header's corner: on the full page the List button, "N of M", Previous and Next; in the peek Previous,
 * Next, the frame switch and Close. Stepping is held while `held` says why — an edit open — since moving would drop it.
 */
export function RecordCornerControls({
  stepper,
  held,
  onClose,
}: {
  stepper?: RecordStepper;
  /** Why stepping is unavailable just now; absent when it is. */
  held?: string;
  onClose: () => void;
}) {
  const showClose = !stepper || stepper.frame === "peek";
  if (inRecordBar(stepper)) {
    const { slot } = stepper.bar;
    return slot ? createPortal(<StepperButtons stepper={stepper} held={held} />, slot) : null;
  }
  return (
    <div className="absolute end-3.5 top-3.5 flex items-center gap-0.5">
      {stepper && <StepperButtons stepper={stepper} held={held} />}
      {showClose && (
        <button type="button" onClick={onClose} aria-label="Close" className={CORNER_BUTTON}>
          <Icon d={ICONS.close} size={16} />
        </button>
      )}
    </div>
  );
}

function StepperButtons({ stepper, held }: { stepper: RecordStepper; held?: string }) {
  return (
    <>
      {stepper.frame === "page" && stepper.lead}
      {stepper.frame === "page" && stepper.position && (
        <span className="me-1 font-u-num text-meta tabular-nums text-u-text3">
          {stepper.position.index + 1} of {stepper.position.total}
        </span>
      )}
      {stepsShown(stepper) && (
        <>
          <button
            type="button"
            aria-label="Previous executive"
            title={held ?? "Previous executive (K)"}
            disabled={!stepper.onPrevious || held !== undefined}
            onClick={stepper.onPrevious}
            className={CORNER_BUTTON}
          >
            <Icon d={ICONS.chevronDown} size={16} className="rotate-180" />
          </button>
          <button
            type="button"
            aria-label="Next executive"
            title={held ?? "Next executive (J)"}
            disabled={!stepper.onNext || held !== undefined}
            onClick={stepper.onNext}
            className={CORNER_BUTTON}
          >
            <Icon d={ICONS.chevronDown} size={16} />
          </button>
        </>
      )}
      {stepper.onToggleFrame && (
        <button
          type="button"
          aria-label={stepper.frame === "peek" ? "Open full page" : (stepper.toggleLabel ?? "Exit full page")}
          title={stepper.frame === "peek" ? "Open full page" : `${stepper.toggleLabel ?? "Exit full page"} (Esc)`}
          onClick={stepper.onToggleFrame}
          className={cn(CORNER_BUTTON, "ms-1")}
        >
          <Icon d={stepper.frame === "peek" ? ICONS.fullscreen : ICONS.fullscreenExit} size={15} />
        </button>
      )}
    </>
  );
}

export function cornerButtonCount(stepper: RecordStepper | undefined): number {
  if (!stepper) return 1;
  if (inRecordBar(stepper)) return 0;
  const steps = stepsShown(stepper) ? 2 : 0;
  if (stepper.frame === "page") return steps + 2 + (stepper.onToggleFrame ? 1 : 0);
  return steps + (stepper.onToggleFrame ? 1 : 0) + 1;
}

/**
 * J and K step down and up the list while the record is open, as in a mail client; never while someone types, holds
 * a modifier, is mid-edit (`paused`), or has any window over the record — the same topmost rule as Escape, so a
 * dialog, a popover or a drawer opened over it holds the record still.
 */
export function useRecordStepKeys(stepper: RecordStepper | undefined, paused: boolean) {
  const onNext = stepper?.onNext;
  const onPrevious = stepper?.onPrevious;
  const isTopLayer = useContext(EscapeLayerContext);
  useEffect(() => {
    if (paused || (!onNext && !onPrevious)) return;
    const handleKey = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.altKey || event.shiftKey) return;
      if (isTypingTarget(event.target) || !isTopLayer()) return;
      const key = event.key.toLowerCase();
      const step = key === "j" ? onNext : key === "k" ? onPrevious : undefined;
      if (!step) return;
      event.preventDefault();
      step();
    };
    document.addEventListener("keydown", handleKey);
    return () => document.removeEventListener("keydown", handleKey);
  }, [onNext, onPrevious, paused, isTopLayer]);
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false;
  if (target.isContentEditable) return true;
  return target.closest("input, textarea, select, [contenteditable=''], [contenteditable='true'], [role='textbox']") !== null;
}
